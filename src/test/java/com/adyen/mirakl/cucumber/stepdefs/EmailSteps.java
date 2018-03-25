package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.ShareholderContact;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import cucumber.api.java.en.Then;
import io.restassured.RestAssured;
import io.restassured.response.ResponseBody;
import org.assertj.core.api.Assertions;
import org.awaitility.Duration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;

public class EmailSteps extends StepDefsHelper {

    @Then("^an email will be sent to the seller$")
    public void anEmailWillBeSentToTheSeller() {
        MiraklShop shop = world.miraklShop;
        String email = shop.getContactInformation().getEmail();

        await().untilAsserted(() -> {
                ResponseBody responseBody = RestAssured.get(mailTrapConfiguration.mailTrapEndPoint()).thenReturn().body();
                List<Map<String, Object>> emailLists = responseBody.jsonPath().get();

                String htmlBody = null;
                Assertions.assertThat(emailLists.size()).isGreaterThan(0);
                for (Map list : emailLists) {
                    if (list.get("to_email").equals(email)) {
                        htmlBody = list.get("html_body").toString();
                        Assertions.assertThat(email).isEqualTo(list.get("to_email"));
                        break;
                    } else {
                        Assertions.fail("Email was not found in mailtrap. Email: [%s]", email);
                    }
                }
                Assertions
                    .assertThat(htmlBody).isNotNull();
                Document parsedBody = Jsoup.parse(htmlBody);
                Assertions
                    .assertThat(parsedBody.body().text())
                    .contains(shop.getId())
                    .contains(shop.getContactInformation().getCivility())
                    .contains(shop.getContactInformation().getFirstname())
                    .contains(shop.getContactInformation().getLastname());

                Assertions.assertThat(parsedBody.title()).isEqualTo("Account verification");
            }
        );
    }

    @Then("^a remedial email will be sent for each ubo$")
    public void aRemedialEmailWillBeSentForEachUbo(String title) throws Throwable {

        GetAccountHolderResponse accountHolder = getGetAccountHolderResponse(world.miraklShop);

        List<String> uboEmails = accountHolder.getAccountHolderDetails().getBusinessDetails().getShareholders().stream()
            .map(ShareholderContact::getEmail)
            .collect(Collectors.toList());

        await().untilAsserted(() -> {
                ResponseBody responseBody = RestAssured.get(mailTrapConfiguration.mailTrapEndPoint()).thenReturn().body();
                List<Map<String, Object>> emailLists = responseBody.jsonPath().get();

                List<String> htmlBody = new LinkedList<>();

                Assertions.assertThat(emailLists.size()).isGreaterThan(0);

                boolean foundEmail = emailLists.stream()
                    .anyMatch(map -> map.get("to_email").equals(uboEmails.iterator().next()));

                Assertions.assertThat(foundEmail).isTrue();

                for (String uboEmail : uboEmails) {
                    emailLists.stream()
                        .filter(map -> map.get("to_email").equals(uboEmail))
                        .findAny()
                        .ifPresent(map -> htmlBody.add(map.get("html_body").toString()));
                }

                Assertions.assertThat(htmlBody).isNotEmpty();

                for (String body : htmlBody) {
                    Document parsedBody = Jsoup.parse(body);
                    Assertions
                        .assertThat(parsedBody.body().text())
                        .contains(world.miraklShop.getId());

                    Assertions.assertThat(parsedBody.title()).isEqualTo(title);
                }
            }
        );
    }
}
