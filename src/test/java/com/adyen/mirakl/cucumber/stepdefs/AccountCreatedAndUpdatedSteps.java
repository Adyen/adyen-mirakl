package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.adyen.model.marketpay.GetAccountHolderRequest;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.service.exception.ApiException;
import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import cucumber.api.DataTable;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;

public class AccountCreatedAndUpdatedSteps extends StepDefsHelper {

    private MiraklShop shop;
    private DocumentContext notificationResponse;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Given("^a seller creates a new shop as an (.*)$")
    public void aSellerCreatesANewShopAsAnIndividual(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createShopForIndividual(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @Given("^shop is created as a (.*) where UBO is entered in non-sequential order$")
    public void shopIsCreatedAsABusinessWhereUBOIsEnteredInNonSequentialOrder(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithNonSequentialUBO(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @When("^a seller creates a shop as a (.*) and provides full UBO data")
    public void aSellerCreatesAShopAsABusinessAndProvidesFullUBOData(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithFullUboInfo(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @When("^a seller creates a new shop as a (.*) and does not provide any UBO data")
    public void aSellerCreatesANewShopAsABusinessAndDoesNotProvideAnyUBOData(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithNoUBOs(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @Given("^a Netherlands seller creates a (.*) shop in Mirakl with UBO data and a bankAccount$")
    public void aNetherlandsSellerCreatedABusinessShopInMiraklWithUBODataAndABankAccount(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopForNetherlandsWithUBOs(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @When("^we update the shop by adding more shareholder data$")
    public void weUpdateTheShopByAddingMoreShareholderData(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        shop = miraklUpdateShopApi
            .addMoreUbosToShop(shop, shop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }

    @And("^an AccountHolder will be created in Adyen with status Active$")
    public void anAccountHolderWillBeCreatedInAdyenWithStatusActive() {
        GetAccountHolderRequest accountHolderRequest = new GetAccountHolderRequest();
        accountHolderRequest.setAccountHolderCode(shop.getId());
        await().untilAsserted(() -> {
            try {
                GetAccountHolderResponse accountHolderResponse = adyenAccountService.getAccountHolder(accountHolderRequest);
                Assertions.assertThat(accountHolderResponse.getAccountHolderStatus().getStatus().toString()).isEqualTo("Active");
            } catch (ApiException e) {
                log.error("Failing test due to exception", e);
                Assertions.fail(e.getError().toString());
            }
        });
    }

    @And("^a notification will be sent pertaining to (.*)$")
    public void aNotificationWillBeSentPertainingToACCOUNT_HOLDER_CREATED(String notification) {
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> mappedAdyenNotificationResponse = retrieveAdyenNotificationBody(notification, shop.getId());
            Assertions.assertThat(mappedAdyenNotificationResponse).isNotNull();
            this.notificationResponse = JsonPath.parse(mappedAdyenNotificationResponse);
            Assertions.assertThat(notificationResponse.read("content.accountHolderCode").toString())
                .isEqualTo(shop.getId());
            Assertions.assertThat(notificationResponse.read("eventType").toString())
                .isEqualTo(notification);
        });
    }

    @And("^the shop data is correctly mapped to the Adyen Account$")
    public void theShopDataIsCorrectlyMappedToTheAdyenAccount() throws Exception {
        GetAccountHolderResponse response = retrieveAccountHolderResponse(shop.getId());
        ImmutableList<String> adyen = assertionHelper.adyenIndividualAccountDataBuilder(response).build();
        ImmutableList<String> mirakl = assertionHelper.miraklShopDataBuilder(shop.getContactInformation().getEmail(), shop).build();
        Assertions.assertThat(adyen).containsAll(mirakl);
    }

    @And("^(?:the netherlands shop data is correctly mapped to the Adyen Business Account|the shop data is correctly mapped to the Adyen Business Account)$")
    public void theShopDataIsCorrectlyMappedToTheAdyenBusinessAccount(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        ImmutableList<String> adyen = assertionHelper.adyenShareHolderAccountDataBuilder(shop, notificationResponse).build();
        ImmutableList<String> mirakl = assertionHelper.miraklShopShareHolderDataBuilder(shop, cucumberTable).build();
        Assertions.assertThat(adyen).containsAll(mirakl);
    }

    @And("^the account holder is created in Adyen with status Active$")
    public void theAccountHolderIsCreatedInAdyenWithStatusActive() {
        Assertions.assertThat(notificationResponse.read("content.accountHolderStatus.status").toString())
            .isEqualTo("Active");
    }

    @Then("^no account holder is created in Adyen$")
    public void noAccountHolderIsCreatedInAdyen() throws Exception {
        try {
            retrieveAccountHolderResponse(shop.getId());
        } catch (ApiException e) {
            Assertions
                .assertThat(e.getError().getMessage())
                .contains("Account with accountCode='"+shop.getId()+"' does not exist");
        }
    }

    @And("^the Adyen bankAccountDetails will posses the correct street data$")
    public void theAdyenBankAccountDetailsWillPossesTheCorrectStreetData() {
        String ownerStreet = notificationResponse
            .read("content.accountHolderDetails.bankAccountDetails[0]BankAccountDetail.ownerStreet").toString();
        String ownerHouseNumberOrName = notificationResponse
            .read("content.accountHolderDetails.bankAccountDetails[0]BankAccountDetail.ownerHouseNumberOrName").toString();

        Assertions
            .assertThat(ownerStreet+" "+ownerHouseNumberOrName)
            .contains(shop.getContactInformation().getStreet1());
    }

    @When("^the Mirakl Shop Details have been updated$")
    public void theMiraklShopDetailsHaveBeenUpdated(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        cucumberTable.forEach(row ->
            shop = miraklUpdateShopApi
                .updateExistingShopsContactInfoWithTableData(shop, shop.getId(), miraklMarketplacePlatformOperatorApiClient, row)
        );
    }

    @When("^the Mirakl Shop is updated by adding more shareholder data$")
    public void theMiraklShopIsUpdatedByAddingMoreShareholderData(DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        shop = miraklUpdateShopApi.addSpecificUBOWithData(shop, shop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }
}
