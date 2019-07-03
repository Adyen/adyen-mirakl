/*
 *                       ######
 *                       ######
 * ############    ####( ######  #####. ######  ############   ############
 * #############  #####( ######  #####. ######  #############  #############
 *        ######  #####( ######  #####. ######  #####  ######  #####  ######
 * ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 * ###### ######  #####( ######  #####. ######  #####          #####  ######
 * #############  #############  #############  #############  #####  ######
 *  ############   ############  #############   ############  #####  ######
 *                                      ######
 *                               #############
 *                               ############
 *
 * Adyen Mirakl Connector
 *
 * Copyright (c) 2018 Adyen B.V.
 * This file is open source and available under the MIT license.
 * See the LICENSE file for more info.
 *
 */

package com.adyen.mirakl.cucumber.stepdefs;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.adyen.Client;
import com.adyen.enums.Environment;
import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.adyen.mirakl.web.rest.AdyenNotificationResource;
import com.adyen.mirakl.web.rest.TestUtil;
import com.adyen.model.Amount;
import com.adyen.model.PaymentRequest;
import com.adyen.model.PaymentResult;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.ShareholderContact;
import com.adyen.model.modification.CaptureRequest;
import com.adyen.model.modification.ModificationResult;
import com.adyen.service.Modification;
import com.adyen.service.Payment;
import com.adyen.service.exception.ApiException;
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
import static com.adyen.model.PaymentResult.ResultCodeEnum.AUTHORISED;
import static com.adyen.model.modification.ModificationResult.ResponseEnum.CAPTURE_RECEIVED_;
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

    @Value("${accounts.adyenPal.username}")
    protected String adyenPalUsername;
    @Value("${accounts.adyenPal.password}")
    protected String adyenPalPassword;
    @Value("${accounts.adyenPal.merchantAccount}")
    protected String adyenPalMerchantAccount;
    private PaymentResult paymentResult;
    private Client client;
    private Amount paymentAmount;

    @Before
    public void setup() {
        restAdyenNotificationMockMvc = MockMvcBuilders.standaloneSetup(adyenNotificationResource).build();
    }

    @Given("^a seller creates a shop as an (.*) with bank account information$")
    public void aSellerCreatesAShopAsAnIndividualWithBankAccountInformation(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createShopForIndividualWithBankDetails(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
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
        restAdyenNotificationMockMvc.perform(post("/api/adyen-notifications").contentType(TestUtil.APPLICATION_JSON_UTF8).content(notification)).andExpect(status().is(201));
    }

    @Then("^an (.*) email will be sent to the seller$")
    public void anAccountVerificationEmailWillBeSentToTheSeller(String title) {
        String email = shop.getContactInformation().getEmail();
        validationCheckOnReceivedEmail(title, email, shop);
    }

    @Then("^(.*) notifications with (.*) with status (.*) will be sent by Adyen$")
    public void accountHolderVerificationNotificationsWithIDENTITYVERIFICATIONWithStatusAWAITINGDATAWillBeSentByAdyen(String eventType, String verificationType, String status) {
        await().untilAsserted(() -> notifications = assertOnMultipleVerificationNotifications(eventType, verificationType, status, shop));
    }

    @And("^the notifications are sent to Connector App$")
    public void theNotificationsAreSentToConnectorApp() throws Exception {
        for (DocumentContext notification : notifications) {
            restAdyenNotificationMockMvc.perform(post("/api/adyen-notifications").contentType(TestUtil.APPLICATION_JSON_UTF8).content(notification.jsonString())).andExpect(status().is(201));
            log.info("Notification posted to Connector: [{}]", notification.jsonString());
        }
    }

    @When("^the IDENTITY_VERIFICATION notifications containing INVALID_DATA status are sent to the Connector for each UBO$")
    public void theIDENTITY_VERIFICATIONNotificationsAreSentToTheConnector() throws Throwable {
        List<String> notifications = new LinkedList<>();
        URL url = Resources.getResource("adyenRequests/CUCUMBER_IDENTITY_VERIFICATION_INVALID_DATA.json");
        GetAccountHolderResponse accountHolder = retrieveAccountHolderResponse(shop.getId());
        String accountHolderCode = accountHolder.getAccountHolderCode();
        List<String> shareholderCodes = accountHolder.getAccountHolderDetails()
                                                     .getBusinessDetails()
                                                     .getShareholders()
                                                     .stream()
                                                     .map(ShareholderContact::getShareholderCode)
                                                     .collect(Collectors.toList());
        for (String shareholderCode : shareholderCodes) {
            try {
                notifications.add(Resources.toString(url, Charsets.UTF_8).replaceAll("\\$accountHolderCode\\$", accountHolderCode).replaceAll("\\$shareholderCode\\$", shareholderCode));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        for (String notification : notifications) {
            restAdyenNotificationMockMvc.perform(post("/api/adyen-notifications").contentType(TestUtil.APPLICATION_JSON_UTF8).content(notification)).andExpect(status().is(201));
            log.info("Notification posted to Connector: [{}]", notification);
        }
    }

    @When("^the (.*) notifications containing (.*) status are sent to the Connector$")
    public void theCOMPANY_VERIFICATIONNotificationsContainingINVALID_DATAStatusAreSentToTheConnector(String eventType, String verificationType) throws Throwable {
        URL url = Resources.getResource("adyenRequests/CUCUMBER_" + eventType + "_" + verificationType + ".json");
        String stringJson = Resources.toString(url, Charsets.UTF_8);

        String notification = stringJson.replaceAll("\\$accountHolderCode\\$", shop.getId());
        restAdyenNotificationMockMvc.perform(post("/api/adyen-notifications").contentType(TestUtil.APPLICATION_JSON_UTF8).content(notification)).andExpect(status().is(201));
        log.info("Notification posted to Connector: [{}]", notification);
    }

    @Then("^a remedial email will be sent for each ubo$")
    public void aRemedialEmailWillBeSentForEachUbo(String title) throws Throwable {
        validationCheckOnUboEmails(title, shop);
    }

    @When("^a payment of (.*) (.*) has been authorised$")
    public void aPaymentOfAmountCurrencyHasBeenAuthorised(String amount, String currency) throws Throwable {
        this.client = new Client(adyenPalUsername, adyenPalPassword, Environment.TEST, "adyen-mirakl-connector-tests");
        Payment payment = new Payment(client);
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setMerchantAccount(adyenPalMerchantAccount);
        paymentRequest.setReference("top-up-mirakl-auth");
        paymentRequest.setAmountData(amount, currency);
        paymentRequest.setCardData("5136333333333335", "John Doe", "10", "2020", "737");

        try {
            this.paymentAmount = paymentRequest.getAmount();
            this.paymentResult = payment.authorise(paymentRequest);
            Assertions.assertThat(paymentResult.getResultCode()).isEqualTo(AUTHORISED);
        } catch (ApiException e) {
            log.error(e.getError().toString());
            throw e;
        }
    }

    @Then("^the payment is captured$")
    public void thePaymentIsCaptured() throws Throwable {
        Modification modification = new Modification(client);
        CaptureRequest captureRequest = new CaptureRequest().merchantAccount(adyenPalMerchantAccount).originalReference(paymentResult.getPspReference());
        captureRequest.setModificationAmount(this.paymentAmount);
        String amountMinorUnits = String.valueOf(this.paymentAmount.getValue());
        Map<String, String> additionalData = captureRequest.getOrCreateAdditionalData();
        additionalData.put("split.api", "1");
        additionalData.put("split.nrOfItems", "1");
        additionalData.put("split.totalAmount", amountMinorUnits);
        additionalData.put("split.currencyCode", this.paymentAmount.getCurrency());
        additionalData.put("split.item1.amount", amountMinorUnits);
        additionalData.put("split.item1.type", "MarketPlace");
        additionalData.put("split.item1.account", configSourceAccountCode);
        additionalData.put("split.item1.reference", "top-up-mirakl-cap");
        additionalData.put("split.item1.description", "splitdescription");

        ModificationResult modificationResult = null;
        try {
            modificationResult = modification.capture(captureRequest);
        } catch (ApiException e) {
            log.error(e.getError().toString());
            throw e;
        }
        Assertions.assertThat(modificationResult.getResponse()).isEqualTo(CAPTURE_RECEIVED_);
    }
}
