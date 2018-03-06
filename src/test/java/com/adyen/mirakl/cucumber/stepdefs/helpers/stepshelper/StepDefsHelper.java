package com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper;

import com.adyen.mirakl.cucumber.stepdefs.helpers.hooks.StartUpTestingHook;
import com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi.MiraklShopApi;
import com.adyen.mirakl.cucumber.stepdefs.helpers.restassured.RestAssuredAdyenApi;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;
import org.assertj.core.api.Assertions;
import org.awaitility.Duration;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class StepDefsHelper {

    @Resource
    private RestAssuredAdyenApi restAssuredAdyenApi;
    @Resource
    private StartUpTestingHook startUpTestingHook;

    protected static volatile MiraklCreatedShops createdShops;
    protected static volatile MiraklShop foundShop;

    protected void waitForNotification() {
        await().atMost(new Duration(30, TimeUnit.MINUTES)).untilAsserted(() -> {
            boolean endpointHasReceivedANotification = restAssuredAdyenApi.endpointHasANotification(startUpTestingHook.getBaseRequestBinUrlPath());
            Assertions.assertThat(endpointHasReceivedANotification).isTrue();
        });
    }

    protected MiraklShop getMiraklShop(MiraklMarketplacePlatformOperatorApiClient client, String seller) {
        MiraklGetShopsRequest shopsRequest = new MiraklGetShopsRequest();
        shopsRequest.setPaginate(false);

        MiraklShops shops = client.getShops(shopsRequest);
        return shops.getShops().stream()
            .filter(shop -> seller.equals(shop.getId())).findAny()
            .orElseThrow(() -> new IllegalStateException("Cannot find shop"));
    }

    protected MiraklShop retrieveMiraklShopByFiltersOnShopEmail(MiraklCreatedShops createdShops, MiraklMarketplacePlatformOperatorApiClient client, MiraklShopApi miraklShopApi) {
        String email = createdShops.getShopReturns().iterator().next().getShopCreated().getContactInformation().getEmail();
        return miraklShopApi.filterMiraklShopsByEmailAndReturnShop(client, email);
    }
}
