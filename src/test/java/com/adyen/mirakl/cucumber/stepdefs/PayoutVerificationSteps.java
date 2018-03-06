package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import cucumber.api.DataTable;
import cucumber.api.java.en.Then;
import org.assertj.core.api.Assertions;

import java.util.Map;

import static com.adyen.mirakl.cucumber.stepdefs.helpers.hooks.CucumberHooks.*;

public class PayoutVerificationSteps extends StepDefsHelper {

    @Then("^adyen will send the (.*) notification$")
    public void adyenWillSendTheACCOUNT_HOLDER_PAYOUTNotification(String notification, DataTable table) throws Throwable {
        cucumberTable.put("table", table);
        String accountHolderCode = cucumberMap.get("accountHolderCode").toString();

        waitForNotification();
        Map<String, Object> adyenNotificationBody = getAdyenNotificationBody(notification, accountHolderCode);
        DocumentContext content = JsonPath.parse(adyenNotificationBody.get("content"));
        rows().forEach(row-> {
            Assertions.assertThat(row.get("currency"))
                .withFailMessage("Currency was not correct.")
                .isEqualTo(content.read("amounts[0].Amount.currency"));

            Assertions.assertThat(row.get("amount"))
                .withFailMessage("Amount was not correct.")
                .isEqualTo(Integer.toString(content.read("amounts[0].Amount.value")));

            Assertions.assertThat(row.get("iban"))
                .withFailMessage("payment was not made to correct iban.")
                .isEqualTo(content.read("bankAccountDetail.iban"));

            Assertions.assertThat(row.get("statusCode"))
                .withFailMessage("Payment status was not correct.")
                .isEqualTo(content.read("status.statusCode"));
        });
    }

    @Then("^adyen will send the (.*) notification with statusCode$")
    public void adyenWillSendTheACCOUNT_HOLDER_PAYOUTNotificationWithStatusCode(String notification, DataTable table) throws Throwable {
        cucumberTable.put("table", table);
        waitForNotification();
        Map<String, Object> adyenNotificationBody = getAdyenNotificationBody(notification, cucumberMap.get("accountHolderCode").toString());
        DocumentContext content = JsonPath.parse(adyenNotificationBody.get("content"));
        Assertions.assertThat(rows().get(0).get("statusCode"))
            .withFailMessage("Status was not correct.")
            .isEqualTo(content.read("status.statusCode"));
    }
}
