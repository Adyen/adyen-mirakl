package com.adyen.mirakl.listeners;

import com.adyen.mirakl.config.MailTemplateService;
import com.adyen.mirakl.domain.AdyenNotification;
import com.adyen.mirakl.events.AdyenNotifcationEvent;
import com.adyen.mirakl.repository.AdyenNotificationRepository;
import com.adyen.mirakl.service.RetryPayoutService;
import com.adyen.model.marketpay.AccountPayoutState;
import com.adyen.model.marketpay.KYCCheckStatusData;
import com.adyen.model.marketpay.notification.AccountHolderStatusChangeNotification;
import com.adyen.model.marketpay.notification.AccountHolderVerificationNotification;
import com.adyen.model.marketpay.notification.GenericNotification;
import com.adyen.notification.NotificationHandler;
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

import java.util.List;
import java.util.Locale;

@Component
public class AdyenNotificationListener {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private NotificationHandler notificationHandler;
    private AdyenNotificationRepository adyenNotificationRepository;
    private MailTemplateService mailTemplateService;
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient;
    private RetryPayoutService retryPayoutService;

    public AdyenNotificationListener(final NotificationHandler notificationHandler,
                                     final AdyenNotificationRepository adyenNotificationRepository,
                                     final MailTemplateService mailTemplateService,
                                     MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient) {
        this.notificationHandler = notificationHandler;
        this.adyenNotificationRepository = adyenNotificationRepository;
        this.mailTemplateService = mailTemplateService;
        this.miraklMarketplacePlatformOperatorApiClient = miraklMarketplacePlatformOperatorApiClient;
    }

    @Async
    @EventListener
    public void handleContextRefresh(AdyenNotifcationEvent event) {
        log.info(String.format("Received notification DB id: [%d]", event.getDbId()));
        final AdyenNotification notification = adyenNotificationRepository.findOneById(event.getDbId());
        final GenericNotification genericNotification = notificationHandler.handleMarketpayNotificationJson(notification.getRawAdyenNotification());
        processNotification(genericNotification);
        adyenNotificationRepository.delete(event.getDbId());
    }

    private void processNotification(final GenericNotification genericNotification) {
        if (genericNotification instanceof AccountHolderVerificationNotification) {
            processAccountholderVerificationNotification((AccountHolderVerificationNotification) genericNotification);
        }
        if(genericNotification instanceof AccountHolderStatusChangeNotification) {
            processAccountholderStatusChangeNotification((AccountHolderStatusChangeNotification) genericNotification);
        }
    }

    private void processAccountholderVerificationNotification(final AccountHolderVerificationNotification genericNotification) {
        final KYCCheckStatusData.CheckStatusEnum verificationStatus = genericNotification.getContent().getVerificationStatus();
        final KYCCheckStatusData.CheckTypeEnum verificationType = genericNotification.getContent().getVerificationType();
        if (KYCCheckStatusData.CheckStatusEnum.RETRY_LIMIT_REACHED.equals(verificationStatus) && KYCCheckStatusData.CheckTypeEnum.BANK_ACCOUNT_VERIFICATION.equals(verificationType)) {
            final String shopId = genericNotification.getContent().getAccountHolderCode();
            final MiraklShop shop = getShop(shopId);
            mailTemplateService.sendMiraklShopEmailFromTemplate(shop, Locale.ENGLISH, "bankAccountVerificationEmail", "email.bank.verification.title");
        }
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


    private void processAccountholderStatusChangeNotification(final AccountHolderStatusChangeNotification accountHolderStatusChangeNotification) {
        final AccountPayoutState oldAccountPayoutState = accountHolderStatusChangeNotification.getContent().getOldStatus().getPayoutState();
        final AccountPayoutState newAccountPayoutState = accountHolderStatusChangeNotification.getContent().getNewStatus().getPayoutState();

        if (oldAccountPayoutState.getAllowPayout().equals(false) && newAccountPayoutState.getAllowPayout().equals(true)) {
            // check if there are payout errors to retrigger
            String accountHolderCode = accountHolderStatusChangeNotification.getContent().getAccountHolderCode();
            retryPayoutService.retryFailedPayoutsForAccountHolder(accountHolderCode);
        }
    }

}
