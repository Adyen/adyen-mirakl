package com.adyen.mirakl.listeners;

import com.adyen.mirakl.service.DocService;
import com.adyen.mirakl.service.MailTemplateService;
import com.adyen.mirakl.domain.AdyenNotification;
import com.adyen.mirakl.events.AdyenNotifcationEvent;
import com.adyen.mirakl.repository.AdyenNotificationRepository;
import com.adyen.mirakl.service.RetryPayoutService;
import com.adyen.model.marketpay.GetAccountHolderRequest;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.ShareholderContact;
import com.adyen.model.marketpay.notification.AccountHolderPayoutNotification;
import com.adyen.model.marketpay.notification.AccountHolderStatusChangeNotification;
import com.adyen.model.marketpay.notification.AccountHolderVerificationNotification;
import com.adyen.model.marketpay.notification.GenericNotification;
import com.adyen.model.marketpay.notification.TransferFundsNotification;
import com.adyen.notification.NotificationHandler;
import com.adyen.service.Account;
import com.adyen.service.exception.ApiException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.adyen.mirakl.listeners.AdyenNotificationListener.TemplateAndSubjectKey.getSubject;
import static com.adyen.mirakl.listeners.AdyenNotificationListener.TemplateAndSubjectKey.getTemplate;
import static com.adyen.model.marketpay.KYCCheckStatusData.*;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

@Component
public class AdyenNotificationListener {

    static class TemplateAndSubjectKey {

        private static final Map<String, Map<String, String>> keys;

        static {
            Map<String, Map<String, String>> builder = new HashMap<>();
            builder.put(CheckTypeEnum.IDENTITY_VERIFICATION.toString() + CheckStatusEnum.AWAITING_DATA.toString(),
                        ImmutableMap.of("accountHolderAwaitingIdentityEmail", "email.account.verification.awaiting.id.title"));
            builder.put(CheckTypeEnum.PASSPORT_VERIFICATION.toString() + CheckStatusEnum.AWAITING_DATA.toString(),
                        ImmutableMap.of("accountHolderAwaitingPassportEmail", "email.account.verification.awaiting.passport.title"));
            builder.put(CheckTypeEnum.COMPANY_VERIFICATION.toString() + CheckStatusEnum.AWAITING_DATA.toString(),
                        ImmutableMap.of("companyAwaitingIdData", "email.company.verification.awaiting.id.title"));
            builder.put(CheckTypeEnum.IDENTITY_VERIFICATION.toString() + CheckStatusEnum.INVALID_DATA.toString(),
                        ImmutableMap.of("accountHolderInvalidIdentityEmail", "email.account.verification.invalid.id.title"));
            builder.put(CheckTypeEnum.PASSPORT_VERIFICATION.toString() + CheckStatusEnum.INVALID_DATA.toString(),
                        ImmutableMap.of("accountHolderInvalidPassportEmail", "email.account.verification.invalid.passport.title"));
            builder.put(CheckTypeEnum.COMPANY_VERIFICATION.toString() + CheckStatusEnum.INVALID_DATA.toString(),
                        ImmutableMap.of("companyInvalidIdData", "email.company.verification.invalid.id.title"));
            keys = builder;
        }

        static String getTemplate(CheckTypeEnum type, CheckStatusEnum status) {
            return keys.get(type.toString() + status.toString()).keySet().iterator().next();
        }

        static String getSubject(CheckTypeEnum type, CheckStatusEnum status) {
            return keys.get(type.toString() + status.toString()).values().iterator().next();
        }
    }

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private NotificationHandler notificationHandler;
    private AdyenNotificationRepository adyenNotificationRepository;
    private MailTemplateService mailTemplateService;
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient;
    private RetryPayoutService retryPayoutService;
    private Account adyenAccountService;
    private DocService docService;

    AdyenNotificationListener(final NotificationHandler notificationHandler,
                              final AdyenNotificationRepository adyenNotificationRepository,
                              final MailTemplateService mailTemplateService,
                              final MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient,
                              final Account adyenAccountService,
                              final RetryPayoutService retryPayoutService,
                              final DocService docService) {
        this.notificationHandler = notificationHandler;
        this.adyenNotificationRepository = adyenNotificationRepository;
        this.mailTemplateService = mailTemplateService;
        this.miraklMarketplacePlatformOperatorApiClient = miraklMarketplacePlatformOperatorApiClient;
        this.adyenAccountService = adyenAccountService;
        this.retryPayoutService = retryPayoutService;
        this.docService = docService;
    }

    @Async
    @EventListener
    public void handleContextRefresh(AdyenNotifcationEvent event) {
        log.info(String.format("Received notification DB id: [%d]", event.getDbId()));
        final AdyenNotification notification = adyenNotificationRepository.findOneById(event.getDbId());
        final GenericNotification genericNotification = notificationHandler.handleMarketpayNotificationJson(notification.getRawAdyenNotification());
        try {
            processNotification(genericNotification);
            adyenNotificationRepository.delete(event.getDbId());
        } catch (ApiException e) {
            log.error("Failed processing notification: {}", e.getError(), e);
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage(), e);
        }
    }

    private void processNotification(final GenericNotification genericNotification) throws Exception {
        if (genericNotification instanceof AccountHolderVerificationNotification) {
            processAccountholderVerificationNotification((AccountHolderVerificationNotification) genericNotification);
        }
        if (genericNotification instanceof AccountHolderStatusChangeNotification) {
            processAccountHolderStatusChangeNotification((AccountHolderStatusChangeNotification) genericNotification);
        }
        if (genericNotification instanceof AccountHolderPayoutNotification) {
            processAccountHolderPayout((AccountHolderPayoutNotification) genericNotification);
        }
        if (genericNotification instanceof TransferFundsNotification) {
            processTransferFunds((TransferFundsNotification) genericNotification);
        }
    }

    private void processAccountholderVerificationNotification(final AccountHolderVerificationNotification verificationNotification) throws Exception {
        final CheckStatusEnum verificationStatus = verificationNotification.getContent().getVerificationStatus();
        final CheckTypeEnum verificationType = verificationNotification.getContent().getVerificationType();
        final String shopId = verificationNotification.getContent().getAccountHolderCode();
        if (CheckStatusEnum.RETRY_LIMIT_REACHED.equals(verificationStatus) && CheckTypeEnum.BANK_ACCOUNT_VERIFICATION.equals(verificationType)) {
            final MiraklShop shop = getShop(shopId);
            mailTemplateService.sendMiraklShopEmailFromTemplate(shop, Locale.getDefault(), "bankAccountVerificationEmail", "email.bank.verification.title");
        } else if (awaitingDataForIdentityOrPassport(verificationStatus, verificationType) || invalidDataForIdentityOrPassport(verificationStatus, verificationType)) {
            final GetAccountHolderRequest getAccountHolderRequest = new GetAccountHolderRequest();
            getAccountHolderRequest.setAccountHolderCode(shopId);
            final GetAccountHolderResponse accountHolderResponse = adyenAccountService.getAccountHolder(getAccountHolderRequest);
            final String shareholderCode = verificationNotification.getContent().getShareholderCode();
            final ShareholderContact shareholderContact = accountHolderResponse.getAccountHolderDetails()
                                                                               .getBusinessDetails()
                                                                               .getShareholders()
                                                                               .stream()
                                                                               .filter(x -> x.getShareholderCode().equals(shareholderCode))
                                                                               .findAny()
                                                                               .orElseThrow(() -> new IllegalStateException("Unable to find shareholder: " + shareholderCode));
            mailTemplateService.sendShareholderEmailFromTemplate(shareholderContact,
                                                                 shopId,
                                                                 Locale.getDefault(),
                                                                 getTemplate(verificationType, verificationStatus),
                                                                 getSubject(verificationType, verificationStatus));
        } else if (invalidOrAwaitingCompanyVerificationData(verificationStatus, verificationType)) {
            final MiraklShop shop = getShop(shopId);
            mailTemplateService.sendMiraklShopEmailFromTemplate(shop, Locale.getDefault(), getTemplate(verificationType, verificationStatus), getSubject(verificationType, verificationStatus));
        } else if(dataProvidedForPassportOrIdentity(verificationStatus, verificationType, CheckStatusEnum.DATA_PROVIDED, CheckTypeEnum.PASSPORT_VERIFICATION, CheckTypeEnum.IDENTITY_VERIFICATION)){
            docService.removeMiraklMediaForShareHolder(verificationNotification.getContent().getShareholderCode());
        }
    }

    private boolean dataProvidedForPassportOrIdentity(final CheckStatusEnum verificationStatus, final CheckTypeEnum verificationType, final CheckStatusEnum dataProvided, final CheckTypeEnum passportVerification, final CheckTypeEnum identityVerification) {
        return dataProvided.equals(verificationStatus) &&
            (passportVerification.equals(verificationType) || identityVerification.equals(verificationType));
    }

    private boolean invalidOrAwaitingCompanyVerificationData(final CheckStatusEnum verificationStatus, final CheckTypeEnum verificationType) {
        return CheckTypeEnum.COMPANY_VERIFICATION.equals(verificationType) && (CheckStatusEnum.INVALID_DATA.equals(verificationStatus)
            || CheckStatusEnum.AWAITING_DATA.equals(verificationStatus));
    }

    private boolean awaitingDataForIdentityOrPassport(final CheckStatusEnum verificationStatus, final CheckTypeEnum verificationType) {
        return dataProvidedForPassportOrIdentity(verificationStatus, verificationType,CheckStatusEnum.AWAITING_DATA,CheckTypeEnum.IDENTITY_VERIFICATION,CheckTypeEnum.PASSPORT_VERIFICATION);
    }

    private boolean invalidDataForIdentityOrPassport(final CheckStatusEnum verificationStatus, final CheckTypeEnum verificationType) {
        return dataProvidedForPassportOrIdentity(verificationStatus, verificationType,CheckStatusEnum.INVALID_DATA,CheckTypeEnum.IDENTITY_VERIFICATION,CheckTypeEnum.PASSPORT_VERIFICATION);
    }

    private MiraklShop getShop(String shopId) {
        final MiraklGetShopsRequest miraklGetShopsRequest = new MiraklGetShopsRequest();
        miraklGetShopsRequest.setShopIds(ImmutableSet.of(shopId));
        final List<MiraklShop> shops = miraklMarketplacePlatformOperatorApiClient.getShops(miraklGetShopsRequest).getShops();
        if (CollectionUtils.isEmpty(shops)) {
            throw new IllegalStateException("Cannot find shop: " + shopId);
        }
        return shops.iterator().next();
    }


    private void processAccountHolderStatusChangeNotification(final AccountHolderStatusChangeNotification accountHolderStatusChangeNotification) {
        final Boolean oldPayoutState = accountHolderStatusChangeNotification.getContent().getOldStatus().getPayoutState().getAllowPayout();
        final Boolean newPayoutState = accountHolderStatusChangeNotification.getContent().getNewStatus().getPayoutState().getAllowPayout();

        if (FALSE.equals(oldPayoutState) && TRUE.equals(newPayoutState)) {
            mailTemplateService.sendMiraklShopEmailFromTemplate(getShop(accountHolderStatusChangeNotification.getContent().getAccountHolderCode()),
                                                                Locale.getDefault(),
                                                                "nowPayable",
                                                                "email.account.status.now.true.title");
        } else if (TRUE.equals(oldPayoutState) && FALSE.equals(newPayoutState)) {
            mailTemplateService.sendMiraklShopEmailFromTemplate(getShop(accountHolderStatusChangeNotification.getContent().getAccountHolderCode()),
                                                                Locale.getDefault(),
                                                                "payoutRevoked",
                                                                "email.account.status.now.false.title");
        }

        if (FALSE.equals(oldPayoutState) && TRUE.equals(newPayoutState)) {
            // check if there are payout errors to retrigger
            String accountHolderCode = accountHolderStatusChangeNotification.getContent().getAccountHolderCode();
            retryPayoutService.retryFailedPayoutsForAccountHolder(accountHolderCode);
        }
    }

    private void processAccountHolderPayout(final AccountHolderPayoutNotification accountHolderPayoutNotification) {
        if (accountHolderPayoutNotification.getContent().getStatus().getStatusCode().equals("Failed")) {
            mailTemplateService.sendOperatorEmailPayoutFailure(getShop(accountHolderPayoutNotification.getContent().getAccountHolderCode()),
                                                               accountHolderPayoutNotification.getContent().getStatus().getMessage());
        }
    }

    private void processTransferFunds(final TransferFundsNotification transferFundsNotification) throws Exception {
        if (transferFundsNotification.getContent().getStatus().getStatusCode().equals("Failed")) {

            // retrieve accountHolderCode from accountCode
            final GetAccountHolderRequest getAccountHolderSourceRequest = new GetAccountHolderRequest();
            getAccountHolderSourceRequest.setAccountCode(transferFundsNotification.getContent().getSourceAccountCode());
            final GetAccountHolderResponse accountHolderSourceResponse = adyenAccountService.getAccountHolder(getAccountHolderSourceRequest);

            final GetAccountHolderRequest getAccountHolderDestinationRequest = new GetAccountHolderRequest();
            getAccountHolderDestinationRequest.setAccountCode(transferFundsNotification.getContent().getDestinationAccountCode());
            final GetAccountHolderResponse accountHolderDestinationResponse = adyenAccountService.getAccountHolder(getAccountHolderDestinationRequest);




            mailTemplateService.sendOperatorEmailTransferFundsFailure(accountHolderSourceResponse.getAccountHolderCode(),
                                                                      accountHolderDestinationResponse.getAccountHolderCode(),
                                                                      transferFundsNotification.getContent().getAmount(),
                                                                      transferFundsNotification.getContent().getTransferCode(),
                                                                      transferFundsNotification.getContent().getStatus().getMessage());
        }
    }
}
