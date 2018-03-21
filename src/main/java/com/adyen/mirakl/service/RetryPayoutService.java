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
import com.adyen.service.Fund;
import com.adyen.service.exception.ApiException;
import com.google.common.reflect.TypeToken;
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
            log.info("No failed payouts found for this accountHolder with accountHolderCode" + accountHolderCode);
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

    public void processFailedPayout(List<AdyenPayoutError> failedPayouts)
    {
        putFailedPayoutInProcessing(failedPayouts);

        failedPayouts.forEach(adyenPayoutError -> {

            PayoutAccountHolderResponse payoutAccountHolderResponse = null;
            try {
                payoutAccountHolderResponse = adyenFundService.payoutAccountHolder(GSON.fromJson(adyenPayoutError.getRawRequest(), new TypeToken<PayoutAccountHolderRequest>() {
                }.getType()));
            } catch (ApiException e) {
                log.error("Failed retry payout exception: " + e.getError(), e);
                updateFailedPayout(adyenPayoutError, payoutAccountHolderResponse);
            } catch (Exception e) {
                log.error("Failed retry payout exception: " + e.getMessage(), e);
                updateFailedPayout(adyenPayoutError, payoutAccountHolderResponse);
            }
        });
    }

    /**
     * to solve possible race-condition between cronjob update and notification update
     */
    protected void putFailedPayoutInProcessing(List<AdyenPayoutError> failedPayouts)
    {
        failedPayouts.forEach(adyenPayoutError ->{
            adyenPayoutError.setProcessing(true);
            adyenPayoutErrorRepository.save(adyenPayoutError);
        });
    }

    protected void updateFailedPayout(AdyenPayoutError adyenPayoutError, PayoutAccountHolderResponse payoutAccountHolderResponse)
    {
        adyenPayoutError.setRetry(adyenPayoutError.getRetry() + 1);
        adyenPayoutError.setProcessing(false);

        if (payoutAccountHolderResponse != null) {
            String rawResponse = GSON.toJson(payoutAccountHolderResponse);
            adyenPayoutError.setRawResponse(rawResponse);
        }
        adyenPayoutErrorRepository.save(adyenPayoutError);
    }
}
