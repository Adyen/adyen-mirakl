package com.adyen.mirakl.service;

import java.util.List;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import com.adyen.mirakl.config.ApplicationProperties;
import com.adyen.mirakl.domain.AdyenPayoutError;
import com.adyen.mirakl.repository.AdyenPayoutErrorRepository;
import com.adyen.model.marketpay.PayoutAccountHolderRequest;
import com.adyen.model.marketpay.PayoutAccountHolderResponse;
import com.adyen.model.marketpay.TransferFundsRequest;
import com.adyen.model.marketpay.TransferFundsResponse;
import com.adyen.service.Fund;
import com.adyen.service.exception.ApiException;
import com.google.common.reflect.TypeToken;
import liquibase.util.StringUtils;
import static com.adyen.mirakl.service.PayoutService.GSON;

@Service
@Transactional
public class RetryPayoutService {

    private final Logger log = LoggerFactory.getLogger(RetryPayoutService.class);

    @Resource
    private AdyenPayoutErrorRepository adyenPayoutErrorRepository;

    @Resource
    private ApplicationProperties applicationProperties;

    @Resource
    private Fund adyenFundService;


    public void retryFailedPayoutsForAccountHolder(String accountHolderCode) {
        final List<AdyenPayoutError> failedPayouts = adyenPayoutErrorRepository.findByAccountHolderCode(accountHolderCode);
        if (CollectionUtils.isEmpty(failedPayouts)) {
            log.info("No failed payouts found for this accountHolder with accountHolderCode: " + accountHolderCode);
            return;
        }
        processFailedPayout(failedPayouts);
    }

    public void retryFailedPayouts() {
        final List<AdyenPayoutError> failedPayouts = adyenPayoutErrorRepository.findByRetry(applicationProperties.getMaxPayoutFailed());
        if (CollectionUtils.isEmpty(failedPayouts)) {
            log.info("No failed payouts found");
            return;
        }
        processFailedPayout(failedPayouts);
    }

    public void processFailedPayout(List<AdyenPayoutError> failedPayouts) {
        putFailedPayoutInProcessing(failedPayouts);

        failedPayouts.forEach(adyenPayoutError -> {
            PayoutAccountHolderResponse payoutAccountHolderResponse = null;
            TransferFundsResponse transferFundsResponse = null;
            try {

                if (! StringUtils.isEmpty(adyenPayoutError.getRawSubscriptionRequest())) {
                    TransferFundsRequest transferFundsRequest = GSON.fromJson(adyenPayoutError.getRawSubscriptionRequest(), new TypeToken<TransferFundsRequest>() {
                    }.getType());
                    transferFundsResponse = adyenFundService.transferFunds(transferFundsRequest);
                    log.info("Subscription submitted for accountHolder: [{}] + Response: [{}]", adyenPayoutError.getAccountHolderCode(), transferFundsResponse);
                }

                PayoutAccountHolderRequest payoutAccountHolderRequest = GSON.fromJson(adyenPayoutError.getRawRequest(), new TypeToken<PayoutAccountHolderRequest>() {
                }.getType());

                payoutAccountHolderResponse = adyenFundService.payoutAccountHolder(payoutAccountHolderRequest);
                log.info("Payout submitted for accountHolder: [{}] + Psp ref: [{}]", payoutAccountHolderRequest.getAccountHolderCode(), payoutAccountHolderResponse.getPspReference());

                // remove from database
                adyenPayoutErrorRepository.delete(adyenPayoutError);
            } catch (ApiException e) {
                log.error("Failed retry payout exception: {}, {}. For the Shop: {}", e.getError(), e, adyenPayoutError.getAccountHolderCode());
                updateFailedPayout(adyenPayoutError, payoutAccountHolderResponse, transferFundsResponse);
            } catch (Exception e) {
                log.error("Failed retry payout exception: {}, {}. For the Shop: {}", e.getMessage(), e, adyenPayoutError.getAccountHolderCode());
                updateFailedPayout(adyenPayoutError, payoutAccountHolderResponse, transferFundsResponse);
            }
        });
    }

    /**
     * to solve possible race-condition between cronjob update and notification update
     */
    protected void putFailedPayoutInProcessing(List<AdyenPayoutError> failedPayouts) {
        failedPayouts.forEach(adyenPayoutError -> {
            adyenPayoutError.setProcessing(true);
            adyenPayoutErrorRepository.save(adyenPayoutError);
        });
    }

    protected void updateFailedPayout(AdyenPayoutError adyenPayoutError, PayoutAccountHolderResponse payoutAccountHolderResponse, TransferFundsResponse transferFundsResponse) {
        adyenPayoutError.setRetry(adyenPayoutError.getRetry() + 1);
        adyenPayoutError.setProcessing(false);

        if (payoutAccountHolderResponse != null) {
            String rawResponse = GSON.toJson(payoutAccountHolderResponse);
            adyenPayoutError.setRawResponse(rawResponse);
        }

        //Subscription went well but payout failed
        if (transferFundsResponse != null) {
            adyenPayoutError.setRawSubscriptionRequest(null);
        }
        adyenPayoutErrorRepository.save(adyenPayoutError);
    }
}
