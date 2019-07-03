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

package com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper;

import java.io.File;
import java.net.URL;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.assertj.core.api.Assertions;
import org.awaitility.Duration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import com.adyen.mirakl.AdyenMiraklConnectorApp;
import com.adyen.mirakl.config.AdyenAccountConfiguration;
import com.adyen.mirakl.config.AdyenConfiguration;
import com.adyen.mirakl.config.MailTrapConfiguration;
import com.adyen.mirakl.config.MiraklOperatorConfiguration;
import com.adyen.mirakl.config.ShopConfiguration;
import com.adyen.mirakl.cucumber.stepdefs.helpers.hooks.StartUpTestingHook;
import com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi.MiraklShopApi;
import com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi.MiraklUpdateShopApi;
import com.adyen.mirakl.cucumber.stepdefs.helpers.restassured.RestAssuredAdyenApi;
import com.adyen.mirakl.repository.AdyenPayoutErrorRepository;
import com.adyen.mirakl.service.DocService;
import com.adyen.mirakl.service.RetryPayoutService;
import com.adyen.mirakl.service.ShopService;
import com.adyen.model.Amount;
import com.adyen.model.marketpay.AccountHolderBalanceRequest;
import com.adyen.model.marketpay.AccountHolderBalanceResponse;
import com.adyen.model.marketpay.DocumentDetail;
import com.adyen.model.marketpay.GetAccountHolderRequest;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.ShareholderContact;
import com.adyen.model.marketpay.TransferFundsRequest;
import com.adyen.model.marketpay.TransferFundsResponse;
import com.adyen.model.marketpay.UploadDocumentRequest;
import com.adyen.model.marketpay.UploadDocumentResponse;
import com.adyen.service.Account;
import com.adyen.service.Fund;
import com.adyen.service.exception.ApiException;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShopReturn;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;
import io.restassured.RestAssured;
import io.restassured.response.ResponseBody;
import static com.google.common.io.Files.toByteArray;
import static org.awaitility.Awaitility.await;
import static org.awaitility.pollinterval.FibonacciPollInterval.fibonacci;

@WebAppConfiguration
@SpringBootTest
@ContextConfiguration(classes = AdyenMiraklConnectorApp.class)
public class StepDefsHelper {

    @Resource
    protected RestAssuredAdyenApi restAssuredAdyenApi;
    @Resource
    protected StartUpTestingHook startUpTestingHook;
    @Resource
    protected MiraklShopApi miraklShopApi;
    @Resource
    protected MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient;
    @Resource
    protected AssertionHelper assertionHelper;
    @Resource
    protected ShopService shopService;
    @Resource
    protected DocService docService;
    @Resource
    protected StartUpTestingHook startUpCucumberHook;
    @Resource
    protected Account adyenAccountService;
    @Resource
    private Fund adyenFundService;
    @Resource
    protected ShopConfiguration shopConfiguration;
    @Resource
    private AdyenAccountConfiguration adyenAccountConfiguration;
    @Resource
    private AdyenConfiguration adyenConfiguration;
    @Resource
    protected MiraklUpdateShopApi miraklUpdateShopApi;
    @Resource
    private MailTrapConfiguration mailTrapConfiguration;
    @Resource
    protected RetryPayoutService retryPayoutService;
    @Resource
    protected AdyenPayoutErrorRepository adyenPayoutErrorRepository;
    @Resource
    protected MiraklOperatorConfiguration miraklOperatorConfiguration;

    @Value("${payoutService.subscriptionTransferCode}")
    protected String subscriptionTransferCode;
    @Value("${payoutService.liableAccountCode}")
    protected String liableAccountCode;
    @Value("${accounts.accountCode.zeroBalanceSourceAccountCode}")
    protected String zeroBalanceSourceAccountCode;
    @Value("${accounts.accountCode.sourceAccountCode}")
    protected String configSourceAccountCode;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    protected void waitForNotification() {
        await().with().pollInterval(fibonacci()).atMost(new Duration(5, TimeUnit.MINUTES)).untilAsserted(() -> {
            boolean endpointHasReceivedANotification = restAssuredAdyenApi.endpointHasANotification(startUpTestingHook.getBaseRequestBinUrlPath());
            Assertions.assertThat(endpointHasReceivedANotification).isTrue();
        });
    }

    protected GetAccountHolderResponse getGetAccountHolderResponse(MiraklShop shop) throws Exception {
        GetAccountHolderRequest getAccountHolderRequest = new GetAccountHolderRequest();
        getAccountHolderRequest.setAccountHolderCode(shop.getId());
        return adyenAccountService.getAccountHolder(getAccountHolderRequest);
    }

    // use for scenarios which don't require verificationType verification
    protected Map<String, Object> retrieveAdyenNotificationBody(String notification, String accountHolderCode) {
        Map<String, Object> adyenNotificationBody = new HashMap<>();
        Map<String, Object> notificationBody = restAssuredAdyenApi.getAdyenNotificationBody(startUpTestingHook.getBaseRequestBinUrlPath(), accountHolderCode, notification, null);
        Assertions.assertThat(adyenNotificationBody).withFailMessage("No data in endpoint.").isNotNull();
        if (notificationBody != null) {
            adyenNotificationBody.putAll(notificationBody);
        } else {
            Assertions.fail(String.format("Notification: [%s] was not found for accountHolderCode: [%s] in endpoint: [%s]",
                                          notification,
                                          accountHolderCode,
                                          startUpTestingHook.getBaseRequestBinUrlPath()));
        }
        return adyenNotificationBody;
    }

    protected MiraklShop getMiraklShop(MiraklMarketplacePlatformOperatorApiClient client, String shopId) {
        MiraklGetShopsRequest shopsRequest = new MiraklGetShopsRequest();
        shopsRequest.setShopIds(ImmutableList.of(shopId));

        MiraklShops shops = client.getShops(shopsRequest);
        return shops.getShops().iterator().next();
    }

    protected MiraklShop retrieveCreatedShop(MiraklCreatedShops shopForIndividualWithBankDetails) {
        return shopForIndividualWithBankDetails.getShopReturns().stream().map(MiraklCreatedShopReturn::getShopCreated).findFirst().orElse(null);
    }

    protected GetAccountHolderResponse retrieveAccountHolderResponse(String accountHolderCode) throws Exception {
        GetAccountHolderRequest request = new GetAccountHolderRequest();
        request.setAccountHolderCode(accountHolderCode);
        return adyenConfiguration.adyenAccountService().getAccountHolder(request);
    }

    private TransferFundsResponse transferFundsAndRetrieveResponse(Long transferAmount, String currency, Integer sourceAccountCode, Integer destinationAccountCode) throws Exception {
        TransferFundsRequest transferFundsRequest = new TransferFundsRequest();
        Amount amount = new Amount();
        amount.setValue(transferAmount);
        if (currency != null) {
            amount.setCurrency(currency);
        } else {
            amount.setCurrency("EUR");
        }
        transferFundsRequest.setAmount(amount);
        transferFundsRequest.setSourceAccountCode(sourceAccountCode.toString());
        transferFundsRequest.setDestinationAccountCode(destinationAccountCode.toString());
        transferFundsRequest.setTransferCode(subscriptionTransferCode);
        return adyenFundService.transferFunds(transferFundsRequest);
    }

    protected void uploadPassportToAdyen(MiraklShop shop) throws Exception {
        URL url = Resources.getResource("fileuploads/passportFront.png");
        File file = new File(url.getPath());
        byte[] bytes = toByteArray(file);
        Base64.Encoder encoder = Base64.getEncoder();
        String encoded = encoder.encodeToString(bytes);

        UploadDocumentRequest uploadDocumentRequest = new UploadDocumentRequest();
        uploadDocumentRequest.setDocumentContent(encoded);

        DocumentDetail documentDetail = new DocumentDetail();
        documentDetail.setAccountHolderCode(shop.getId());
        documentDetail.setDescription("PASSED");
        documentDetail.setDocumentType(DocumentDetail.DocumentTypeEnum.valueOf("PASSPORT"));
        documentDetail.setFilename("passportFront.png");
        uploadDocumentRequest.setDocumentDetail(documentDetail);
        try {
            UploadDocumentResponse response = adyenAccountService.uploadDocument(uploadDocumentRequest);
            Assertions.assertThat(response.getAccountHolderCode()).isEqualTo(shop.getId());
        } catch (ApiException e) {
            log.error(e.getError().toString());
            throw e;
        }
    }

    protected void transferAccountHolderBalance(List<Map<String, String>> cucumberTable, MiraklShop shop) throws Exception {
        Long transferAmount = Long.valueOf(cucumberTable.get(0).get("transfer amount"));

        String currency = cucumberTable.get(0).get("currency");
        GetAccountHolderResponse accountHolder = getGetAccountHolderResponse(shop);
        transferAmountAndAssert(transferAmount, currency, accountHolder);

        await().untilAsserted(() -> {
            AccountHolderBalanceRequest accountHolderBalanceRequest = new AccountHolderBalanceRequest();
            accountHolderBalanceRequest.setAccountHolderCode(shop.getId());
            AccountHolderBalanceResponse balance = adyenFundService.AccountHolderBalance(accountHolderBalanceRequest);

            Assertions.assertThat(balance.getTotalBalance().getBalance().stream().map(Amount::getValue).findAny().orElse(null)).isEqualTo(transferAmount);
        });
        log.info(String.format("\nAmount transferred successfully to [%s]", shop.getId()));
    }

    protected void transferAccountHolderBalanceFromAZeroBalanceAccount(List<Map<String, String>> cucumberTable, MiraklShop shop) {
        try {
            Long transferAmount = Long.valueOf(cucumberTable.get(0).get("transfer amount"));
            String currency = null;
            if (cucumberTable.get(0).get("currency") != null) {
                currency = cucumberTable.get(0).get("currency");
            }
            GetAccountHolderResponse accountHolder = getGetAccountHolderResponse(shop);
            transferAmountFromZeroBalanceAccount(transferAmount, currency, accountHolder);
            AccountHolderBalanceRequest accountHolderBalanceRequest = new AccountHolderBalanceRequest();
            accountHolderBalanceRequest.setAccountHolderCode(shop.getId());
            adyenFundService.AccountHolderBalance(accountHolderBalanceRequest);
        } catch (Exception e) {
            log.info(e.getMessage());
        }
    }

    protected void transferAccountHolderBalanceBeyondTier(List<Map<String, String>> cucumberTable, MiraklShop shop) throws Exception {
        Long transferAmount = Long.valueOf(cucumberTable.get(0).get("transfer amount"));
        GetAccountHolderResponse accountHolder = getGetAccountHolderResponse(shop);
        String currency = null;
        if (cucumberTable.get(0).get("currency") != null) {
            currency = cucumberTable.get(0).get("currency");
        }
        transferAmountAndAssert(transferAmount, currency, accountHolder);

        await().untilAsserted(() -> {
            AccountHolderBalanceRequest accountHolderBalanceRequest = new AccountHolderBalanceRequest();
            accountHolderBalanceRequest.setAccountHolderCode(shop.getId());
            AccountHolderBalanceResponse balance = adyenFundService.AccountHolderBalance(accountHolderBalanceRequest);

            Assertions.assertThat(balance.getTotalBalance().getBalance().stream().map(Amount::getValue).findAny().orElse(null)).isGreaterThan(transferAmount);
        });
        log.info(String.format("\nAmount transferred successfully to [%s]", shop.getId()));
    }

    private void transferAmountAndAssert(Long transferAmount, String currency, GetAccountHolderResponse accountHolder) {
        accountHolder.getAccounts().stream().map(com.adyen.model.marketpay.Account::getAccountCode).findAny().ifPresent(accountCode -> {
            Integer destinationAccountCode = Integer.valueOf(accountCode);
            Integer sourceAccountCode = adyenAccountConfiguration.getAccountCode().get("sourceAccountCode");

            TransferFundsResponse response = null;
            try {
                response = transferFundsAndRetrieveResponse(transferAmount, currency, sourceAccountCode, destinationAccountCode);
            } catch (ApiException e) {
                log.error(e.getError().toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
            assert response != null;
            Assertions.assertThat(response.getResultCode()).isEqualTo("Success");
        });
    }

    private void transferAmountFromZeroBalanceAccount(Long transferAmount, String currency, GetAccountHolderResponse accountHolder) {
        accountHolder.getAccounts().stream().map(com.adyen.model.marketpay.Account::getAccountCode).findAny().ifPresent(accountCode -> {
            Integer destinationAccountCode = Integer.valueOf(accountCode);
            Integer sourceAccountCode = adyenAccountConfiguration.getAccountCode().get("zeroBalanceSourceAccountCode");

            try {
                transferFundsAndRetrieveResponse(transferAmount, currency, sourceAccountCode, destinationAccountCode);
            } catch (ApiException e) {
                log.error(e.getError().getMessage(), e);
                throw new IllegalStateException(e);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                throw new IllegalStateException(e);
            }
        });
    }

    protected void validationCheckOnReceivedEmail(String title, String email, MiraklShop shop) {
        await().with().pollInterval(fibonacci()).untilAsserted(() -> {
            ResponseBody responseBody = RestAssured.get(mailTrapConfiguration.mailTrapEndPoint()).thenReturn().body();
            final String response = responseBody.asString();
            if (response.equalsIgnoreCase("{\"error\":\"Throttled\"}")) {
                log.warn("Mail throttled, will try again");
            }
            Assertions.assertThat(response).isNotEqualToIgnoringCase("{\"error\":\"Throttled\"}");

            List<Map<String, Object>> emailLists = responseBody.jsonPath().get();

            String htmlPath = null;
            Assertions.assertThat(emailLists.size()).isGreaterThan(0);
            for (Map list : emailLists) {
                if (list.get("to_email").equals(email)) {
                    htmlPath = list.get("html_path").toString();
                    Assertions.assertThat(email).isEqualTo(list.get("to_email"));
                    break;
                }
                Assertions.fail("Email was not found in mailtrap. Email: [%s]", email);
            }

            Assertions.assertThat(htmlPath).isNotNull();
            Document parsedBody = getHtmlBodyFromHtmlPath(htmlPath);
            Assertions.assertThat(parsedBody).isNotNull();
            Assertions.assertThat(parsedBody.body().text())
                      .contains(shop.getId())
                      .contains(shop.getContactInformation().getCivility())
                      .contains(shop.getContactInformation().getFirstname())
                      .contains(shop.getContactInformation().getLastname());

            Assertions.assertThat(parsedBody.title()).isEqualTo(title);
        });
    }

    protected void validationCheckOnIndividualEmails(String title, MiraklShop shop) throws Exception {
        GetAccountHolderResponse accountHolder = retrieveAccountHolderResponse(shop.getId());

        String accountEmail = accountHolder.getAccountHolderDetails().getEmail();

        validationCheckOnReceivedEmails(title, shop, Collections.singletonList(accountEmail));
    }

    protected void validationCheckOnUboEmails(String title, MiraklShop shop) throws Exception {
        GetAccountHolderResponse accountHolder = retrieveAccountHolderResponse(shop.getId());

        List<String> uboEmails = accountHolder.getAccountHolderDetails().getBusinessDetails().getShareholders().stream().map(ShareholderContact::getEmail).collect(Collectors.toList());

        validationCheckOnReceivedEmails(title, shop, uboEmails);
    }

    private void validationCheckOnReceivedEmails(String title, MiraklShop shop, List<String> emailAddresses) {
        AtomicReference<List<String>> atomicReference = new AtomicReference<>();

        await().with().pollInterval(fibonacci()).untilAsserted(() -> {
            ResponseBody responseBody = RestAssured.get(mailTrapConfiguration.mailTrapEndPoint()).thenReturn().body();
            final String response = responseBody.asString();
            if (response.equalsIgnoreCase("{\"error\":\"Throttled\"}")) {
                log.warn("Mail throttled, will try again");
            }
            Assertions.assertThat(response).isNotEqualToIgnoringCase("{\"error\":\"Throttled\"}");

            List<Map<String, Object>> receivedEmails = responseBody.jsonPath().getList("");
            Assertions.assertThat(receivedEmails).size().isGreaterThan(0);

            boolean foundEmail = receivedEmails.stream().anyMatch(map -> map.get("to_email").equals(emailAddresses.iterator().next()));
            Assertions.assertThat(foundEmail).isTrue();

            List<String> htmlPath = new LinkedList<>();
            for (String email : emailAddresses) {
                receivedEmails.stream().filter(map -> map.get("to_email").equals(email)).findAny().ifPresent(map -> htmlPath.add(map.get("html_path").toString()));
            }

            Assertions.assertThat(htmlPath).isNotEmpty();
            Assertions.assertThat(htmlPath).hasSize(emailAddresses.size());
            atomicReference.set(htmlPath);
        });

        List<String> htmlPath = atomicReference.get();
        for (String path : htmlPath) {
            Document parsedHtmlBody = getHtmlBodyFromHtmlPath(path);
            Assertions.assertThat(parsedHtmlBody).isNotNull();
            Assertions.assertThat(parsedHtmlBody.body().text()).contains(shop.getId());
            Assertions.assertThat(parsedHtmlBody.title()).isEqualTo(title);
        }
    }

    protected Document getHtmlBodyFromHtmlPath(String htmlPath) {
        ResponseBody responseHtmlBody = RestAssured.get(mailTrapConfiguration.mailTrapHtmlBodyEndPoint(htmlPath)).thenReturn().body();
        if (responseHtmlBody != null) {
            final String responseHtml = responseHtmlBody.asString();
            return Jsoup.parse(responseHtml);
        }
        return null;
    }

    protected ImmutableList<DocumentContext> assertOnMultipleVerificationNotifications(String eventType, String verificationType, String verificationStatus, MiraklShop shop) throws Exception {
        waitForNotification();
        // get shareholderCodes from Adyen
        GetAccountHolderResponse accountHolder = getGetAccountHolderResponse(shop);

        List<String> shareholderCodes = accountHolder.getAccountHolderDetails()
                                                     .getBusinessDetails()
                                                     .getShareholders()
                                                     .stream()
                                                     .map(ShareholderContact::getShareholderCode)
                                                     .collect(Collectors.toList());

        log.info("Shareholders found: [{}]", shareholderCodes.size());
        // get all ACCOUNT_HOLDER_VERIFICATION notifications
        AtomicReference<ImmutableList<DocumentContext>> atomicReference = new AtomicReference<>();
        await().untilAsserted(() -> {
            List<DocumentContext> notifications = restAssuredAdyenApi.getMultipleAdyenNotificationBodies(startUpTestingHook.getBaseRequestBinUrlPath(), shop.getId(), eventType, verificationType, verificationStatus);
            ImmutableList<DocumentContext> verificationNotifications = restAssuredAdyenApi.extractShareHolderNotifications(notifications, shareholderCodes);
            Assertions.assertThat(verificationNotifications).withFailMessage("Notification is empty.").isNotEmpty();

            Assertions.assertThat(verificationNotifications.size())
                      .withFailMessage("Correct number of notifications were not found. Found: <%s>", verificationNotifications.size())
                      .isEqualTo(shareholderCodes.size());

            atomicReference.set(verificationNotifications);
        });
        return atomicReference.get();
    }

    protected ImmutableList<DocumentContext> assertOnVerificationNotification(String eventType, String verificationType, String verificationStatus, MiraklShop shop) throws Exception {
        waitForNotification();

        // get all ACCOUNT_HOLDER_VERIFICATION notifications
        AtomicReference<ImmutableList<DocumentContext>> atomicReference = new AtomicReference<>();
        await().untilAsserted(() -> {
            List<DocumentContext> notifications = restAssuredAdyenApi.getMultipleAdyenNotificationBodies(startUpTestingHook.getBaseRequestBinUrlPath(), shop.getId(), eventType, verificationType, verificationStatus);
            Assertions.assertThat(notifications).withFailMessage("Notification is empty.").isNotEmpty();

            ImmutableList.Builder<DocumentContext> notificationsBuilder = new ImmutableList.Builder<>();
            notificationsBuilder.addAll(notifications);
            atomicReference.set(notificationsBuilder.build());
        });
        return atomicReference.get();
    }

    protected DocumentContext retrieveAndExtractTransferNotifications(String eventType, String status, String sourceAccountCode, String destinationAccountCode, String transferCode) {
        AtomicReference<DocumentContext> atomicDocContext = new AtomicReference<>();
        await().untilAsserted(() -> {
            DocumentContext transferNotification = null;
            ImmutableList<DocumentContext> notificationBodies = restAssuredAdyenApi.getMultipleAdyenTransferNotifications(startUpCucumberHook.getBaseRequestBinUrlPath(), eventType, transferCode);
            Assertions.assertThat(notificationBodies).isNotEmpty();

            if (notificationBodies.size() > 1) {
                for (DocumentContext notification : notificationBodies) {
                    transferNotification = restAssuredAdyenApi.extractCorrectTransferNotification(notification, sourceAccountCode, destinationAccountCode);
                    if (transferNotification != null) {
                        break;
                    }
                }
            } else {
                transferNotification = notificationBodies.get(0);
            }
            Assertions.assertThat(transferNotification).isNotNull();
            Assertions.assertThat(transferNotification.read("content.status.statusCode").toString()).isEqualTo(status);
            atomicDocContext.set(transferNotification);
        });
        return atomicDocContext.get();
    }

    protected String retrieveAdyenAccountCode(MiraklShop shop) throws Exception {
        GetAccountHolderResponse response = getGetAccountHolderResponse(shop);
        return response.getAccounts().stream().map(com.adyen.model.marketpay.Account::getAccountCode).findAny().orElse(null);
    }
}
