package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.hooks.StartUpCucumberHook;
import com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi.MiraklShopApi;
import com.adyen.mirakl.cucumber.stepdefs.helpers.restassured.RestAssuredAdyenApi;
import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.AssertionHelper;
import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.JsonPath;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import cucumber.api.DataTable;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.assertj.core.api.Assertions;
import org.awaitility.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;


public class AccountHolderVerificationSteps {
    @Resource
    private MiraklShopApi miraklShopApi;
    @Resource
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient;
    @Resource
    private StartUpCucumberHook startUpCucumberHook;
    private MiraklCreatedShops createdShops;
    private String shopId;
    @Resource
    private RestAssuredAdyenApi restAssuredAdyenApi;
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private boolean createShareHolderDate = false;
    @Resource
    private AssertionHelper assertionHelper;
    private MiraklShop miraklShop;
    private String email;
    private Map<String, Object> content;
    Map<String, Object> adyenNotificationBody;

    @Given("^a shop has been created in Mirakl with a corresponding account holder in Adyen with the following data$")
    public void aShopHasBeenCreatedInMiraklWithACorrespondingAccountHolderInAdyenWithTheFollowingData(DataTable table) throws Throwable {
        List<Map<Object, Object>> tableMap = table.getTableConverter().toMaps(table, String.class, String.class);
        tableMap.forEach(map -> createdShops = miraklShopApi.createNewShop(miraklMarketplacePlatformOperatorApiClient, map, createShareHolderDate));
    }

    @Then("^the (.*) notification is sent by Adyen comprising of (.*) and (.*)")
    public void theACCOUNT_HOLDER_VERIFICATIONNotificationIsSentByAdyenComprisingOfBANK_ACCOUNT_VERIFICATIONAndPASSED(String notification,
                                                                                                                      String verificationType,
                                                                                                                      String verificationStatus) throws Throwable {

        shopId = createdShops.getShopReturns().iterator().next().getShopCreated().getId();

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {

            Map<String, Object> adyenNotificationBody = restAssuredAdyenApi
                .getAdyenNotificationBody(startUpCucumberHook.getBaseRequestBinUrlPath(), shopId, notification, verificationType);

            Assertions.assertThat(adyenNotificationBody).isNotNull();
            Assertions.assertThat(JsonPath.parse(adyenNotificationBody.get("content"))
                .read("verificationStatus").toString()).isEqualTo(verificationStatus);
            Assertions.assertThat(JsonPath.parse(adyenNotificationBody.get("content"))
                .read("verificationType").toString()).isEqualTo(verificationType);
        });
    }

    @Then("^a new bankAccountDetail will be created for the existing Account Holder$")
    public void aNewBankAccountDetailWillBeCreatedForTheExistingAccountHolder(DataTable table) throws Throwable {
        List<Map<Object, Object>> tableMap = table.getTableConverter().toMaps(table, String.class, String.class);
        shopId = createdShops.getShopReturns().iterator().next().getShopCreated().getId();
        email = createdShops.getShopReturns().iterator().next().getShopCreated().getContactInformation().getEmail();
        miraklShop = miraklShopApi.filterMiraklShopsByEmailAndReturnShop(miraklMarketplacePlatformOperatorApiClient, email);

        await().atMost(Duration.TEN_MINUTES).untilAsserted(() -> {
            String eventType = tableMap.get(0).get("eventType").toString();
            adyenNotificationBody = restAssuredAdyenApi
                .getAdyenNotificationBody(startUpCucumberHook.getBaseRequestBinUrlPath(), shopId, eventType, null);

            Assertions.assertThat(adyenNotificationBody).isNotEmpty().withFailMessage("Notification has not been sent yet.");
            content = JsonPath.parse(adyenNotificationBody.get("content")).read("accountHolderDetails.bankAccountDetails[0]BankAccountDetail");

            ImmutableList<String> miraklBankAccountDetail = assertionHelper.miraklBankAccountInformation(miraklShop).build();
            ImmutableList<String> adyenBankAccountDetail = assertionHelper.adyenBankAccountDetail(content).build();

            Assertions.assertThat(JsonPath.parse(content).read("primaryAccount").toString()).isEqualTo("true");
            Assertions.assertThat(JsonPath.parse(content).read("bankAccountUUID").toString()).isNotEmpty();

            Assertions.assertThat(miraklBankAccountDetail).containsAll(adyenBankAccountDetail);
        });
    }

    @And("^a new IBAN has been provided by the seller in Mirakl and the mandatory IBAN fields have been provided$")
    public void aNewIBANHasBeenProvidedByTheSellerInMiraklAndTheMandatoryIBANFieldsHaveBeenProvided() throws Throwable {
        shopId = createdShops.getShopReturns().iterator().next().getShopCreated().getId();
        miraklShopApi.updateExistingShop(createdShops, shopId, miraklMarketplacePlatformOperatorApiClient, false);
    }

    @When("^the IBAN has been modified in Mirakl$")
    public void theIBANHasBeenModifiedInMirakl() throws Throwable {
        shopId = createdShops.getShopReturns().iterator().next().getShopCreated().getId();
        miraklShopApi.updateExistingShop(createdShops, shopId, miraklMarketplacePlatformOperatorApiClient, true);
    }

    @And("^the previous BankAccountDetail will be removed$")
    public void thePreviousBankAccountDetailWillBeRemoved() throws Throwable {
        Map<String, Object> content = JsonPath.parse(adyenNotificationBody.get("content")).read("accountHolderDetails.bankAccountDetails[0]");
        Assertions.assertThat(content).hasSize(1);
    }
}
