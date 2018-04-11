package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import cucumber.api.DataTable;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;

public class ExistingShopUpdateSteps extends StepDefsHelper {

    private String additionalFieldName;
    private String seller;
    private String legalEntity;
    private MiraklShop miraklShop;
    private DocumentContext notificationResponse;

    @Given("^the operator has specified that the (.*) is an (.*)")
    public void theOperatorHasSpecifiedThatTheSellerIsAnLegalEntity(String seller, String legalEntity) {
        this.seller = shopConfiguration.shopIds.get(seller).toString();
        this.legalEntity = legalEntity;
    }

    @Given("^a shop exists in Mirakl$")
    public void updateShopExistsInMirakl(DataTable table) {
        List<Map<Object, Object>> rows = table.getTableConverter().toMaps(table, String.class, String.class);
        String seller = shopConfiguration.shopIds.get(rows.get(0).get("seller").toString()).toString();
        this.miraklShop = getMiraklShop(miraklMarketplacePlatformOperatorApiClient, seller);
    }

    @When("^the operator views the shop information using S20 Mirakl API call$")
    public void theOperatorViewsTheShopInformationUsingSMiraklAPICall() {
        MiraklShop miraklShop = getMiraklShop(miraklMarketplacePlatformOperatorApiClient, this.seller);

        this.additionalFieldName = miraklShop.getAdditionalFieldValues().stream()
            .filter(addFields -> addFields instanceof MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue)
            .map(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue.class::cast)
            .map(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue::getValue)
            .filter(legalEntity::equalsIgnoreCase)
            .findAny().orElse(null);
    }

    @Then("^the sellers legal entity will be displayed as (.*)$")
    public void theSellersLegalEntityWillBeDisplayedAsLegalEntity(String legalEntity) {
        Assertions.assertThat(additionalFieldName).isEqualToIgnoringCase(legalEntity);
    }

    @Given("^a shop exists in Mirakl with the following fields$")
    public void aShopExistsInMirakl(DataTable table) {
        List<Map<Object, Object>> rows = table.getTableConverter().toMaps(table, String.class, String.class);
        this.seller = shopConfiguration.shopIds.get(rows.get(0).get("seller").toString()).toString();
        this.miraklShop = getMiraklShop(miraklMarketplacePlatformOperatorApiClient, seller);
        Assertions.assertThat(miraklShop.getId()).isEqualTo(this.seller);
        rows.forEach(row-> {
            Assertions.assertThat(row.get("lastName")).isEqualTo(miraklShop.getContactInformation().getLastname());
            Assertions.assertThat(row.get("city")).isEqualTo(miraklShop.getContactInformation().getCity());
        });
    }

    @When("^the Mirakl Shop Details have been updated as the same as before$")
    public void theMiraklShopDetailsHaveBeenUpdatedAsTheSameAsBefore(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        cucumberTable.forEach(row ->
           this.miraklShop = miraklUpdateShopApi
                .updateExistingShopsContactInfoWithTableData(miraklShop, miraklShop.getId(), miraklMarketplacePlatformOperatorApiClient, row)
        );
    }

    @And("^a notification of (.*) will not be sent$")
    public void aNotificationOfACCOUNT_HOLDER_UPDATEDWillNotBeSent(String notification) {
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> response = restAssuredAdyenApi
                .getAdyenNotificationBody(startUpCucumberHook.getBaseRequestBinUrlPath(), this.miraklShop.getId(), notification, null);
            Assertions.assertThat(response).isNull();
        });
    }

    @When("^the Mirakl Shop Details have been updated with invalid data$")
    public void theMiraklShopDetailsHaveBeenUpdatedWithInvalidData(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        this.miraklShop = miraklUpdateShopApi.updateUboDataWithInvalidData(this.miraklShop, this.miraklShop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }

    @Then("^an email will be sent to the seller$")
    public void anEmailWillBeSentToTheSeller(String title) {
        String email = this.miraklShop.getContactInformation().getEmail();
        validationCheckOnReceivedEmail(title, email, this.miraklShop);
    }

    @When("^the Mirakl Shop Details have been changed")
    public void theMiraklShopDetailsHaveBeenchanged() {
        this.miraklShop = miraklUpdateShopApi
            .updateExistingShopAddressFirstLine(this.miraklShop, this.miraklShop.getId(), miraklMarketplacePlatformOperatorApiClient);
    }

    @And("^the (.*) will be sent by Adyen$")
    public void theACCOUNT_HOLDER_UPDATEDWillBeSentByAdyen(String notification) {
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> mappedAdyenNotificationResponse = retrieveAdyenNotificationBody(notification, miraklShop.getId());
            Assertions.assertThat(mappedAdyenNotificationResponse).isNotNull();
            notificationResponse = JsonPath.parse(mappedAdyenNotificationResponse);
            Assertions.assertThat(notificationResponse.read("content.accountHolderCode").toString())
                .isEqualTo(miraklShop.getId());
            Assertions.assertThat(notificationResponse.read("eventType").toString())
                .isEqualTo(notification);
        });
    }
}
