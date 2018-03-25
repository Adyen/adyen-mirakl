package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.ShareholderContact;
import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import cucumber.api.DataTable;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import io.restassured.RestAssured;
import io.restassured.response.ResponseBody;
import org.assertj.core.api.Assertions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;

public class MiraklAdyenSteps extends StepDefsHelper {

    @And("^the shop data is correctly mapped to the Adyen Account$")
    public void theShopDataIsCorrectlyMappedToTheAdyenAccount() {
        ImmutableList<String> adyen = assertionHelper.adyenAccountDataBuilder(world.notificationResponse).build();
        ImmutableList<String> mirakl = assertionHelper.miraklShopDataBuilder(world.miraklShop.getContactInformation().getEmail(), world.miraklShop).build();
        Assertions.assertThat(adyen).containsAll(mirakl);
    }

    @And("^the shop data is correctly mapped to the Adyen Business Account$")
    public void theShopDataIsCorrectlyMappedToTheAdyenBusinessAccount(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);

        ImmutableList<String> adyen = assertionHelper.adyenShareHolderAccountDataBuilder(world.notificationResponse).build();
        ImmutableList<String> mirakl = assertionHelper.miraklShopShareHolderDataBuilder(world.miraklShop, cucumberTable).build();
        Assertions.assertThat(adyen).containsAll(mirakl);
    }

    @Given("^a new (.*) shop has been created in Mirakl with some Mandatory data missing$")
    public void aNewBusinessShopHasBeenCreatedInMiraklWithoutMandatoryShareholderInformation(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithMissingUboInfo(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        world.miraklShop = retrieveCreatedShop(shops);
    }
}
