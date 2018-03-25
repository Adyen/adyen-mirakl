package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.web.rest.AdyenNotificationResource;
import com.adyen.mirakl.web.rest.TestUtil;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.ShareholderContact;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.jayway.jsonpath.DocumentContext;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


public class ConnectorAppAdyenSteps extends StepDefs {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private AdyenNotificationResource adyenNotificationResource;

    private MockMvc restAdyenNotificationMockMvc;

    @Before
    public void setup() {
        this.restAdyenNotificationMockMvc = MockMvcBuilders.standaloneSetup(adyenNotificationResource).build();
    }

    @When("^a RETRY_LIMIT_REACHED verificationStatus has been sent to the Connector$")
    public void aRETRY_LIMIT_REACHEDVerificationStatusHasBeenSentToTheConnector(String notificationTemplate) throws Throwable {
        MiraklShop shop = world.miraklShop;
        String notification = notificationTemplate.replaceAll("\\$shopId\\$", shop.getId());
        restAdyenNotificationMockMvc.perform(post("/api/adyen-notifications")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(notification))
            .andExpect(status().is(201));
    }

    @And("^the notifications are sent to Connector App$")
    public void theNotificationsAreSentToConnectorApp() throws Exception {
        for (DocumentContext notification : world.notifications) {
            restAdyenNotificationMockMvc.perform(post("/api/adyen-notifications")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(notification.jsonString()))
                .andExpect(status().is(201));
            log.info("Notification posted to Connector: [%s]", notification.jsonString());
        }
    }


    @When("^the IDENTITY_VERIFICATION notifications containing INVALID_DATA status are sent to the Connector for each UBO$")
    public void theIDENTITY_VERIFICATIONNotificationsAreSentToTheConnector() throws Throwable {
        List<String> notifications = new LinkedList<>();
        URL url = Resources.getResource("adyenRequests/CUCUMBER_IDENTITY_VERIFICATION_INVALID_DATA.json");
        MiraklShop shop = world.miraklShop;
        GetAccountHolderResponse accountHolder = getGetAccountHolderResponse(shop);
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
            log.info("Notification posted to Connector: [%s]", notification);
        }
    }
}
