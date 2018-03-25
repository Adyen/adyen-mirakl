package com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper;

import com.adyen.mirakl.config.AdyenAccountConfiguration;
import com.adyen.mirakl.config.MailTrapConfiguration;
import com.adyen.mirakl.config.ShopConfiguration;
import com.adyen.mirakl.cucumber.stepdefs.helpers.World;
import com.adyen.mirakl.cucumber.stepdefs.helpers.hooks.StartUpTestingHook;
import com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi.MiraklShopApi;
import com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi.MiraklUpdateShopApi;
import com.adyen.mirakl.cucumber.stepdefs.helpers.restassured.RestAssuredAdyenApi;
import com.adyen.mirakl.repository.AdyenPayoutErrorRepository;
import com.adyen.mirakl.service.DocService;
import com.adyen.mirakl.service.RetryPayoutService;
import com.adyen.mirakl.service.ShopService;
import com.adyen.model.Amount;
import com.adyen.model.marketpay.GetAccountHolderRequest;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.TransferFundsRequest;
import com.adyen.model.marketpay.TransferFundsResponse;
import com.adyen.service.Account;
import com.adyen.service.Fund;
import com.jayway.jsonpath.DocumentContext;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShopReturn;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;
import org.assertj.core.api.Assertions;
import org.awaitility.Duration;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class StepDefsHelper {

    @Resource
    protected RestAssuredAdyenApi restAssuredAdyenApi;
    @Resource
    protected StartUpTestingHook startUpTestingHook;
    @Resource
    protected MiraklShopApi miraklShopApi;
    @Resource
    protected MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient;
    @Resource
    protected AssertionHelper assertionHelper;
    @Resource
    protected ShopService shopService;
    @Resource
    protected DocService docService;
    @Resource
    protected StartUpTestingHook startUpCucumberHook;
    @Resource
    protected Account adyenAccountService;
    @Resource
    protected Fund adyenFundService;
    @Resource
    protected ShopConfiguration shopConfiguration;
    @Resource
    protected AdyenAccountConfiguration adyenAccountConfiguration;
    @Resource
    protected MiraklUpdateShopApi miraklUpdateShopApi;
    @Resource
    protected MailTrapConfiguration mailTrapConfiguration;
    @Resource
    protected RetryPayoutService retryPayoutService;
    @Resource
    protected AdyenPayoutErrorRepository adyenPayoutErrorRepository;

    @Autowired
    protected World world;


    protected void waitForNotification() {
        await().atMost(new Duration(30, TimeUnit.MINUTES)).untilAsserted(() -> {
            boolean endpointHasReceivedANotification = restAssuredAdyenApi.endpointHasANotification(startUpTestingHook.getBaseRequestBinUrlPath());
            Assertions.assertThat(endpointHasReceivedANotification).isTrue();
        });
    }

    protected GetAccountHolderResponse getGetAccountHolderResponse(MiraklShop shop) throws Exception {
        GetAccountHolderRequest getAccountHolderRequest = new GetAccountHolderRequest();
        getAccountHolderRequest.setAccountHolderCode(shop.getId());
        return adyenAccountService.getAccountHolder(getAccountHolderRequest);
    }

    // use for scenarios which don't require verificationType verification
    protected Map<String, Object> retrieveAdyenNotificationBody(String notification, String accountHolderCode) {
        Map<String, Object> adyenNotificationBody = new HashMap<>();
        await().untilAsserted(() -> {
            Map<String, Object> notificationBody = restAssuredAdyenApi
                .getAdyenNotificationBody(startUpTestingHook.getBaseRequestBinUrlPath(),
                    accountHolderCode, notification, null);

            Assertions.assertThat(adyenNotificationBody).withFailMessage("No data in endpoint.").isNotNull();
            if (notificationBody != null) {
                adyenNotificationBody.putAll(notificationBody);
            } else {
                Assertions.fail(String
                    .format("Notification: [%s] was not found for accountHolderCode: [%s] in endpoint: [%s]",
                        notification, accountHolderCode, startUpTestingHook.getBaseRequestBinUrlPath()));
            }
        });
        return adyenNotificationBody;
    }

    protected MiraklShop getMiraklShop(MiraklMarketplacePlatformOperatorApiClient client, String seller) {
        MiraklGetShopsRequest shopsRequest = new MiraklGetShopsRequest();
        shopsRequest.setPaginate(false);

        MiraklShops shops = client.getShops(shopsRequest);
        return shops.getShops().stream()
            .filter(shop -> seller.equals(shop.getId())).findAny()
            .orElseThrow(() -> new IllegalStateException("Cannot find shop"));
    }

    protected MiraklShop retrieveCreatedShop(MiraklCreatedShops shopForIndividualWithBankDetails) {
        return shopForIndividualWithBankDetails.getShopReturns()
            .stream().map(MiraklCreatedShopReturn::getShopCreated).findFirst().orElse(null);
    }

    protected TransferFundsResponse transferFundsAndRetrieveResponse(Long transferAmount, Integer sourceAccountCode, Integer destinationAccountCode) throws Exception {
        TransferFundsRequest transferFundsRequest = new TransferFundsRequest();
        Amount amount = new Amount();
        amount.setValue(transferAmount);
        amount.setCurrency("EUR");
        transferFundsRequest.setAmount(amount);
        transferFundsRequest.setSourceAccountCode(sourceAccountCode.toString());
        transferFundsRequest.setDestinationAccountCode(destinationAccountCode.toString());
        transferFundsRequest.setTransferCode("TransferCode_1");
        return adyenFundService.transferFunds(transferFundsRequest);
    }
}
