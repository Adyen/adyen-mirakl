package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.adyen.mirakl.domain.AdyenPayoutError;
import com.adyen.mirakl.web.rest.AdyenNotificationResource;
import com.adyen.mirakl.web.rest.MiraklNotificationsResource;
import com.adyen.mirakl.web.rest.TestUtil;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import cucumber.api.DataTable;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AccountPayoutSteps extends StepDefsHelper{

    @Autowired
    private MiraklNotificationsResource miraklNotificationsResource;
    @Autowired
    private AdyenNotificationResource adyenNotificationResource;
    private MiraklShop shop;
    private String accountHolderCode;
    private MockMvc restUserMockMvc;
    private MockMvc restAdyenNotificationMockMvc;
    private List<DocumentContext> notifications;
    private DocumentContext adyenNotificationBody;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Before
    public void setup() {
        this.restUserMockMvc = MockMvcBuilders.standaloneSetup(miraklNotificationsResource).build();
        this.restAdyenNotificationMockMvc = MockMvcBuilders.standaloneSetup(adyenNotificationResource).build();
        Awaitility.setDefaultTimeout(Duration.FIVE_MINUTES);
    }

    @Given("^a shop has been created in Mirakl for an (.*) with mandatory KYC data$")
    public void aShopHasBeenCreatedInMiraklForAnIndividualWithMandatoryKYCData(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createShopForIndividualWithBankDetails(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @And("^a passport has been uploaded to Adyen$")
    public void aPassportHasBeenUploadedToAdyen() throws Throwable {
        uploadPassportToAdyen(shop);
    }

    @When("^the accountHolders balance is increased$")
    public void theAccountHoldersBalanceIsIncreased(DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        accountHolderCode = shop.getId();
        transferAccountHolderBalance(cucumberTable, shop);
    }

    @When("^the PayoutState allowPayout changes from false to true$")
    public void thePayoutStateAllowPayoutChangesFromFalseToTrue() {
        await().untilAsserted(() -> {
            GetAccountHolderResponse account = getGetAccountHolderResponse(shop);
            Boolean allowPayout = account.getAccountHolderStatus().getPayoutState().getAllowPayout();
            Assertions
                .assertThat(allowPayout)
                .isTrue();
            log.info(String.format("Payout status is [%s]", allowPayout.toString()));
        });
    }

    @And("^a notification will be sent in relation to the payout state change$")
    public void aNotficationWillBeSentInRelationToTheBalanceChange(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        String eventType = cucumberTable.get(0).get("eventType");
        String newPayoutState = cucumberTable.get(0).get("newPayoutState");
        String oldPayoutState = cucumberTable.get(0).get("oldPayoutState");

        waitForNotification();
        await().untilAsserted(() -> {
            notifications = restAssuredAdyenApi
                .getMultipleAdyenNotificationBodies(startUpTestingHook.getBaseRequestBinUrlPath(), shop.getId(), eventType, null);

            final Optional<DocumentContext> notification = notifications.stream()
                .filter(x -> x.read("content.oldStatus.payoutState.allowPayout").equals(oldPayoutState))
                .filter(x -> x.read("content.newStatus.payoutState.allowPayout").equals(newPayoutState))
                .findAny();
            Assertions.assertThat(notification.isPresent()).isTrue();

            adyenNotificationBody = notification.get();

        });
    }

    @When("^the notification is sent to the Connector$")
    public void theNotificationIsSentToTheConnector() throws Throwable {
        restAdyenNotificationMockMvc.perform(post("/api/adyen-notifications")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(adyenNotificationBody.jsonString()))
            .andExpect(status().is(201));
        log.info("Notification posted to Connector: [{}]", adyenNotificationBody.jsonString());
    }

    @When("^a payment voucher is sent to the Connector$")
    public void aPaymentVoucherIsSentToAdyen(DataTable table) throws Exception {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        String paymentVoucher = cucumberTable.get(0).get("paymentVoucher");
        URL url = Resources.getResource("paymentvouchers/"+paymentVoucher);
        final String csvFile = Resources.toString(url, Charsets.UTF_8);
        String csv = csvFile.replaceAll("\\$shopId\\$", shop.getId());
        MockMultipartFile mockMultipartFile = new MockMultipartFile("file", paymentVoucher, "text/plain", csv.getBytes());
        restUserMockMvc.perform(MockMvcRequestBuilders.fileUpload("/api/mirakl-notifications/payout")
            .file(mockMultipartFile))
            .andExpect(status().is(200));
    }

    @Then("^adyen will send the (.*) notification$")
    public void adyenWillSendTheACCOUNT_HOLDER_PAYOUTNotification(String notification, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> adyenNotificationBody = retrieveAdyenNotificationBody(notification, accountHolderCode);
            DocumentContext content = JsonPath.parse(adyenNotificationBody.get("content"));
            cucumberTable.forEach(row -> {
                Assertions.assertThat(row.get("statusCode"))
                    .isEqualTo(content.read("status.statusCode"));
                Assertions.assertThat(row.get("currency"))
                    .isEqualTo(content.read("amounts[0].Amount.currency"));
                Assertions.assertThat(row.get("amount"))
                    .isEqualTo(Double.toString(content.read("amounts[0].Amount.value")));
                Assertions.assertThat(row.get("iban"))
                    .isEqualTo(content.read("bankAccountDetail.iban"));
            });
        });
    }

    @Then("^adyen will send the (.*) notification with status$")
    public void adyenWillSendTheACCOUNT_HOLDER_PAYOUTNotificationWithStatusCode(String notification, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> adyenNotificationBody = retrieveAdyenNotificationBody(notification, shop.getId());
            DocumentContext content = JsonPath.parse(adyenNotificationBody.get("content"));
            Assertions.assertThat(cucumberTable.get(0).get("statusCode"))
                .withFailMessage("Status was not correct.")
                .isEqualTo(content.read("status.statusCode"));
            String message = cucumberTable.get(0).get("message");
            if (!message.equals("")) {
                Assertions
                    .assertThat(content.read("status.message.text").toString())
                    .contains(message);
            }
            log.info(content.toString());
            this.adyenNotificationBody = JsonPath.parse(adyenNotificationBody);
        });
    }

    @And("^the failed payout record is removed from the Connector database$")
    public void theFailedPayoutRecordIsRemovedFromTheConnectorDatabase() {
        List<AdyenPayoutError> byAccountHolderCode = adyenPayoutErrorRepository.findByAccountHolderCode(accountHolderCode);
        Assertions
            .assertThat(byAccountHolderCode)
            .isEmpty();
    }

    @And("^balance is transferred from a zero balance account$")
    public void balanceIsTransferredToANonPayoutAccountHolder(DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        transferAccountHolderBalanceFromAZeroBalanceAccount(cucumberTable, shop);
    }

    @And("^the accountHolder receives balance$")
    public void theAccountHolderReceivesBalance(DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        uploadPassportToAdyen(shop);
        transferAccountHolderBalance(cucumberTable, shop);
    }

    @Then("^(.*) notification will be sent by Adyen$")
    public void TRANSFER_FUNDSNotificationWillBeSentByAdyen(String eventType, String status) throws Throwable {
        waitForNotification();
        String accountCode = retrieveAdyenAccountCode(shop);
        retrieveAndExtractTransferNotifications(eventType, status, liableAccountCode, accountCode, subscriptionTransferCode);
    }

    @Then("^adyen will send the (.*) notification using the transferCode$")
    public void adyenWillSendTheTRANSFER_FUNDSNotificationUsingTheTransferCode(String eventType, String status) throws Throwable {
        waitForNotification();
        String accountCode = retrieveAdyenAccountCode(shop);
        adyenNotificationBody = retrieveAndExtractTransferNotifications(eventType, status, zeroBalanceSourceAccountCode, accountCode, transferCode);
    }

    @When("^the accountHolders balance is increased beyond the tier level$")
    public void theAccountHoldersBalanceIsIncreasedBeyondTheTierLevel(DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        accountHolderCode = shop.getId();
        transferAccountHolderBalanceBeyondTier(cucumberTable, shop);
    }

    @When("^the PayoutState allowPayout changes from true to false$")
    public void thePayoutStateAllowPayoutChangesFromTrueToFalse() {
        await().untilAsserted(() -> {
            GetAccountHolderResponse account = getGetAccountHolderResponse(shop);
            Boolean allowPayout = account.getAccountHolderStatus().getPayoutState().getAllowPayout();
            Assertions
                .assertThat(allowPayout)
                .isFalse();
            log.info(String.format("Payout status is [%s]", allowPayout.toString()));
        });
    }

    @Then("^a payout email will be sent to the seller$")
    public void aPayoutEmailWillBeSentToTheSeller(String title) {
        String email = shop.getContactInformation().getEmail();
        validationCheckOnReceivedEmail(title, email, shop);
    }

    @Then("^a payout email will be sent to the operator$")
    public void aPayoutEmailWillBeSentToTheOperator(String title) {
        log.info("Operator email: [{}]",miraklOperatorConfiguration.getMiraklOperatorEmail());
        validationCheckOnReceivedEmail(title, miraklOperatorConfiguration.getMiraklOperatorEmail(), shop);
    }
}
