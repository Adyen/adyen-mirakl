package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.ShareholderContact;
import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import cucumber.api.DataTable;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;
import net.minidev.json.JSONArray;
import org.assertj.core.api.Assertions;
import org.awaitility.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;

public class AdyenNotificationSteps extends StepDefsHelper {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Then("^a new bankAccountDetail will be created for the existing Account Holder$")
    public void aNewBankAccountDetailWillBeCreatedForTheExistingAccountHolder(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        waitForNotification();
        await().untilAsserted(() -> {
            String eventType = cucumberTable.get(0).get("eventType");
            Map<String, Object> adyenNotificationBody = retrieveAdyenNotificationBody(eventType, world.miraklShop.getId());

            List<Map<Object, Object>> bankAccountDetails = JsonPath.parse(adyenNotificationBody
                .get("content"))
                .read("accountHolderDetails.bankAccountDetails");

            ImmutableList<String> miraklBankAccountDetail = assertionHelper.miraklBankAccountInformation(world.miraklShop).build();
            ImmutableList<String> adyenBankAccountDetail = assertionHelper.adyenBankAccountDetail(bankAccountDetails, cucumberTable).build();
            Assertions.assertThat(miraklBankAccountDetail).containsAll(adyenBankAccountDetail);

            Assertions
                .assertThat(assertionHelper.getParsedBankAccountDetail().read("primaryAccount").toString())
                .isEqualTo("true");
            Assertions
                .assertThat(assertionHelper.getParsedBankAccountDetail().read("bankAccountUUID").toString())
                .isNotEmpty();
        });
    }

    @Then("^adyen will send the (.*) comprising of (\\w*) and status of (.*)")
    public void adyenWillSendTheACCOUNT_HOLDER_VERIFICATIONComprisingOfCOMPANY_VERIFICATION(String eventType, String verificationType, String status) {
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> adyenNotificationBody = restAssuredAdyenApi
                .getAdyenNotificationBody(startUpTestingHook.getBaseRequestBinUrlPath(), world.miraklShop.getId(), eventType, verificationType);
            Assertions.assertThat(adyenNotificationBody).isNotEmpty();
            Assertions.assertThat(JsonPath.parse(adyenNotificationBody.get("content"))
                .read("verificationType").toString()).isEqualTo(verificationType);
            Assertions.assertThat(JsonPath.parse(adyenNotificationBody.get("content"))
                .read("verificationStatus").toString()).isEqualTo(status);
        });
    }

    @Then("^adyen will send the (.*) comprising of accountHolder (.*) and status of (.*)")
    public void adyenWillSendTheACCOUNT_HOLDER_VERIFICATIONComprisingOfCOMPANY_VERIFICATIONAccountHolder(String eventType, String verificationType, String status) {
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> adyenNotificationBody = restAssuredAdyenApi
                .getAdyenNotificationBody(startUpTestingHook.getBaseRequestBinUrlPath(), world.miraklShop.getId(), eventType, verificationType);
            Assertions.assertThat(adyenNotificationBody).isNotEmpty();
            Assertions.assertThat(JsonPath.parse(adyenNotificationBody.get("content"))
                .read("verification.accountHolder.checks[0].type").toString()).isEqualTo(verificationType);
            Assertions.assertThat(JsonPath.parse(adyenNotificationBody.get("content"))
                .read("verification.accountHolder.checks[0].status").toString()).isEqualTo(status);
        });
    }

    @Then("^adyen will send the (.*) notification with multiple (.*) of status (.*)")
    public void adyenWillSendTheACCOUNT_HOLDER_UPDATEDNotificationWithMultipleIDENTITY_VERIFICATIONOfStatusDATA_PROVIDED(
        String eventType, String verificationType, String verificationStatus) {
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> adyenNotificationBody = restAssuredAdyenApi
                .getAdyenNotificationBody(startUpCucumberHook.getBaseRequestBinUrlPath(), world.miraklShop.getId(), eventType, verificationType);

            Assertions.assertThat(adyenNotificationBody).isNotEmpty();
            JSONArray shareholderJsonArray = JsonPath.parse(adyenNotificationBody).read("content.verification.shareholders.*");
            for (Object shareholder : shareholderJsonArray) {
                Object checks = JsonPath.parse(shareholder).read("checks[0]");
                Assertions.assertThat(JsonPath.parse(checks).read("type").toString()).isEqualTo(verificationType);
                Assertions.assertThat(JsonPath.parse(checks).read("status").toString()).isEqualTo(verificationStatus);
            }
        });
    }

    @Then("^adyen will send multiple (.*) notifications with (.*) of status (.*)$")
    public void adyenWillSendMultipleACCOUNT_HOLDER_VERIFICATIONNotificationWithIDENTITY_VERIFICATIONOfStatusDATA_PROVIDED(
        String eventType, String verificationType, String verificationStatus, DataTable table) throws Throwable {
        List<Map<String, Integer>> cucumberTable = table.getTableConverter().toMaps(table, String.class, Integer.class);
        waitForNotification();

        // get shareholderCodes from Adyen
        GetAccountHolderResponse accountHolder = getGetAccountHolderResponse(world.miraklShop);

        List<String> shareholderCodes = accountHolder.getAccountHolderDetails().getBusinessDetails().getShareholders().stream()
            .map(ShareholderContact::getShareholderCode)
            .collect(Collectors.toList());

        await().untilAsserted(() -> {
            Integer maxUbos = cucumberTable.get(0).get("maxUbos");
            // get all ACCOUNT_HOLDER_VERIFICATION notifications
            List<DocumentContext> notifications = restAssuredAdyenApi
                .getMultipleAdyenNotificationBodies
                    (startUpTestingHook.getBaseRequestBinUrlPath(), world.miraklShop.getId(), eventType, verificationType, shareholderCodes);

            Assertions
                .assertThat(notifications)
                .withFailMessage("Notification is empty.")
                .isNotEmpty();
            Assertions.assertThat(notifications).hasSize(maxUbos);

            for (DocumentContext notification : notifications) {
                Assertions
                    .assertThat(notification.read("content.verificationStatus").toString())
                    .isEqualTo(verificationStatus);
            }
            world.notifications = notifications;
        });
    }

    @Then("^adyen will send the (.*) notification with status$")
    public void adyenWillSendTheACCOUNT_HOLDER_PAYOUTNotificationWithStatusCode(String notification, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> adyenNotificationBody = retrieveAdyenNotificationBody(notification, world.miraklShop.getId());
            DocumentContext content = JsonPath.parse(adyenNotificationBody.get("content"));
            Assertions.assertThat(cucumberTable.get(0).get("statusCode"))
                .withFailMessage("Status was not correct.")
                .isEqualTo(content.read("status.statusCode"));

            String message = cucumberTable.get(0).get("message");

            Assertions
                .assertThat(content.read("status.message.text").toString())
                .contains(message);

            log.info(content.toString());
        });
    }

    @And("^a notification will be sent pertaining to (.*)$")
    public void aNotificationWillBeSentPertainingToACCOUNT_HOLDER_CREATED(String notification) {
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> mappedAdyenNotificationResponse = retrieveAdyenNotificationBody(notification, world.miraklShop.getId());
            Assertions.assertThat(mappedAdyenNotificationResponse).isNotNull();
            world.notificationResponse = JsonPath.parse(mappedAdyenNotificationResponse);
            Assertions.assertThat(world.notificationResponse.read("content.accountHolderCode").toString())
                .isEqualTo(world.miraklShop.getId());
            Assertions.assertThat(world.notificationResponse.read("eventType").toString())
                .isEqualTo(notification);
        });
    }

    @Then("^no account holder is created in Adyen$")
    public void noAccountHolderIsCreatedInAdyen() {
        await().pollDelay(Duration.TEN_SECONDS).untilAsserted(() -> {
            Map mapResult = restAssuredAdyenApi.getAdyenNotificationBody(startUpCucumberHook
                .getBaseRequestBinUrlPath(), world.miraklShop.getId(), "ACCOUNT_HOLDER_CREATED", null);
            Assertions.assertThat(mapResult == null);
        });
    }

    @And("^the account holder is created in Adyen with status Active$")
    public void theAccountHolderIsCreatedInAdyenWithStatusActive() {
        Assertions.assertThat(world.notificationResponse.read("content.accountHolderStatus.status").toString())
            .isEqualTo("Active");
    }

    @Then("^adyen will send the (.*) notification$")
    public void adyenWillSendTheACCOUNT_HOLDER_PAYOUTNotification(String notification, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> adyenNotificationBody = retrieveAdyenNotificationBody(notification, world.miraklShop.getId());
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

    @Then("^the (.*) notification is sent by Adyen comprising of (.*) and (.*)")
    public void theACCOUNT_HOLDER_VERIFICATIONNotificationIsSentByAdyenComprisingOfBANK_ACCOUNT_VERIFICATIONAndPASSED(String notification,
                                                                                                                      String verificationType,
                                                                                                                      String verificationStatus) {
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> adyenNotificationBody = restAssuredAdyenApi
                .getAdyenNotificationBody(startUpTestingHook.getBaseRequestBinUrlPath(), world.miraklShop.getId(), notification, verificationType);

            Assertions.assertThat(adyenNotificationBody).withFailMessage("No data received from notification endpoint").isNotNull();
            Assertions.assertThat(JsonPath.parse(adyenNotificationBody.get("content"))
                .read("verificationStatus").toString()).isEqualTo(verificationStatus);
            Assertions.assertThat(JsonPath.parse(adyenNotificationBody.get("content"))
                .read("verificationType").toString()).isEqualTo(verificationType);
        });
    }

    @And("^(?:the previous BankAccountDetail will be removed|a notification will be sent in relation to the balance change)$")
    public void thePreviousBankAccountDetailWillBeRemoved(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        String eventType = cucumberTable.get(0).get("eventType");
        String reason = cucumberTable.get(0).get("reason");

        await().untilAsserted(() -> {
            DocumentContext adyenNotificationBody = JsonPath.parse(retrieveAdyenNotificationBody(eventType, world.miraklShop.getId()));
            Assertions.assertThat(adyenNotificationBody.read("content.reason").toString())
                .contains(reason);
        });
    }
}
