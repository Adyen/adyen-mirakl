package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.adyen.mirakl.web.rest.AdyenNotificationResource;
import com.adyen.mirakl.web.rest.TestUtil;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.ShareholderContact;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.jayway.jsonpath.DocumentContext;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import cucumber.api.DataTable;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


public class ConnectorAppAdyenSteps extends StepDefsHelper {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private AdyenNotificationResource adyenNotificationResource;

    private MockMvc restAdyenNotificationMockMvc;
    private MiraklShop shop;
    private ImmutableList<DocumentContext> notifications;

    @Before
    public void setup() {
        restAdyenNotificationMockMvc = MockMvcBuilders.standaloneSetup(adyenNotificationResource).build();
    }

    @Given("^a seller creates a shop as an (.*) with bank account information$")
    public void aSellerCreatesAShopAsAnIndividualWithBankAccountInformation(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi
            .createShopForIndividualWithBankDetails(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @Given("^a seller creates a (.*) shop$")
    public void waNewBusinessShopHasBeenCreatedInMiraklWithoutMandatoryShareholderInformation(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithMissingUboInfo(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @Given("^the seller created a (.*) shop with Invalid Data$")
    public void aNewBusinessShopHasBeenCreatedInMiraklWithInvalidData(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithMissingUboInfo(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @When("^a RETRY_LIMIT_REACHED verificationStatus has been sent to the Connector$")
    public void aRETRY_LIMIT_REACHEDVerificationStatusHasBeenSentToTheConnector(String notificationTemplate) throws Throwable {
        String notification = notificationTemplate.replaceAll("\\$shopId\\$", shop.getId());
        restAdyenNotificationMockMvc.perform(post("/api/adyen-notifications")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(notification))
            .andExpect(status().is(201));
    }

    @Then("^an (.*) email will be sent to the seller$")
    public void anAccountVerificationEmailWillBeSentToTheSeller(String title) {
        String email = shop.getContactInformation().getEmail();
        validationCheckOnReceivedEmail(title, email, shop);
    }

    @Then("^(.*) notifications with (.*) with status (.*) will be sent by Adyen$")
    public void accountHolderVerificationNotificationsWithIDENTITYVERIFICATIONWithStatusAWAITINGDATAWillBeSentByAdyen(String eventType, String verificationType, String status) {
        await().untilAsserted(()-> notifications = assertOnMultipleVerificationNotifications(eventType, verificationType, status, shop));
    }

    @And("^the notifications are sent to Connector App$")
    public void theNotificationsAreSentToConnectorApp() throws Exception {
        for (DocumentContext notification : notifications) {
            restAdyenNotificationMockMvc.perform(post("/api/adyen-notifications")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(notification.jsonString()))
                .andExpect(status().is(201));
            log.info("Notification posted to Connector: [{}]", notification.jsonString());
        }
    }

    @When("^the IDENTITY_VERIFICATION notifications containing INVALID_DATA status are sent to the Connector for each UBO$")
    public void theIDENTITY_VERIFICATIONNotificationsAreSentToTheConnector() throws Throwable {
        List<String> notifications = new LinkedList<>();
        URL url = Resources.getResource("adyenRequests/CUCUMBER_IDENTITY_VERIFICATION_INVALID_DATA.json");
        GetAccountHolderResponse accountHolder = retrieveAccountHolderResponse(shop.getId());
        String accountHolderCode = accountHolder.getAccountHolderCode();
        List<String> shareholderCodes = accountHolder.getAccountHolderDetails().getBusinessDetails().getShareholders().stream()
            .map(ShareholderContact::getShareholderCode)
            .collect(Collectors.toList());
        for (String shareholderCode : shareholderCodes) {
            try {
                notifications.add(Resources.toString(url, Charsets.UTF_8)
                    .replaceAll("\\$accountHolderCode\\$", accountHolderCode)
                    .replaceAll("\\$shareholderCode\\$", shareholderCode));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        for (String notification : notifications) {
            restAdyenNotificationMockMvc.perform(post("/api/adyen-notifications")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(notification))
                .andExpect(status().is(201));
            log.info("Notification posted to Connector: [{}]", notification);
        }
    }

    @When("^the (.*) notifications containing (.*) status are sent to the Connector$")
    public void theCOMPANY_VERIFICATIONNotificationsContainingINVALID_DATAStatusAreSentToTheConnector(String eventType, String verificationType) throws Throwable {
        URL url = Resources.getResource("adyenRequests/CUCUMBER_" + eventType + "_" + verificationType + ".json");
        String stringJson = Resources.toString(url, Charsets.UTF_8);

        String notification = stringJson.replaceAll("\\$accountHolderCode\\$", shop.getId());
        restAdyenNotificationMockMvc.perform(post("/api/adyen-notifications")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(notification))
            .andExpect(status().is(201));
        log.info("Notification posted to Connector: [{}]", notification);
    }

    @Then("^a remedial email will be sent for each ubo$")
    public void aRemedialEmailWillBeSentForEachUbo(String title) throws Throwable {
        validationCheckOnReceivedEmails(title, shop);
    }
}
