package com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper;

import com.adyen.mirakl.AdyenMiraklConnectorApp;
import com.adyen.mirakl.config.*;
import com.adyen.mirakl.cucumber.stepdefs.helpers.hooks.StartUpTestingHook;
import com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi.MiraklShopApi;
import com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi.MiraklUpdateShopApi;
import com.adyen.mirakl.cucumber.stepdefs.helpers.restassured.RestAssuredAdyenApi;
import com.adyen.mirakl.repository.AdyenPayoutErrorRepository;
import com.adyen.mirakl.service.DocService;
import com.adyen.mirakl.service.RetryPayoutService;
import com.adyen.mirakl.service.ShopService;
import com.adyen.model.Amount;
import com.adyen.model.marketpay.*;
import com.adyen.service.Account;
import com.adyen.service.Fund;
import com.adyen.service.exception.ApiException;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.jayway.jsonpath.DocumentContext;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShopReturn;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;
import io.restassured.RestAssured;
import io.restassured.response.ResponseBody;
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

import javax.annotation.Resource;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    @Value("${payoutService.transferCode}")
    protected String transferCode;
    @Value("${payoutService.liableAccountCode}")
    protected String liableAccountCode;
    @Value("${accounts.accountCode.zeroBalanceSourceAccountCode}")
    protected String zeroBalanceSourceAccountCode;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    protected void waitForNotification() {
        await().atMost(new Duration(30, TimeUnit.MINUTES)).untilAsserted(() -> {
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
        Map<String, Object> notificationBody = restAssuredAdyenApi
            .getAdyenNotificationBody(startUpTestingHook.getBaseRequestBinUrlPath(),
                accountHolderCode, notification, null);

        Assertions.assertThat(adyenNotificationBody).withFailMessage("No data in endpoint.").isNotNull();
        if (notificationBody != null) {
            adyenNotificationBody.putAll(notificationBody);
        } else {
            Assertions.fail(String
                .format("Notification: [%s] was not found for accountHolderCode: [%s] in endpoint: [%s]",
                    notification, accountHolderCode, startUpTestingHook.getBaseRequestBinUrlPath()));
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
        return shopForIndividualWithBankDetails.getShopReturns()
            .stream().map(MiraklCreatedShopReturn::getShopCreated).findFirst().orElse(null);
    }

    protected GetAccountHolderResponse retrieveAccountHolderResponse(String accountHolderCode) throws Exception {
        GetAccountHolderRequest request = new GetAccountHolderRequest();
        request.setAccountHolderCode(accountHolderCode);
        return adyenConfiguration.adyenAccountService().getAccountHolder(request);
    }

    private TransferFundsResponse transferFundsAndRetrieveResponse(Long transferAmount, Integer sourceAccountCode, Integer destinationAccountCode) throws Exception {
        TransferFundsRequest transferFundsRequest = new TransferFundsRequest();
        Amount amount = new Amount();
        amount.setValue(transferAmount);
        amount.setCurrency("EUR");
        transferFundsRequest.setAmount(amount);
        transferFundsRequest.setSourceAccountCode(sourceAccountCode.toString());
        transferFundsRequest.setDestinationAccountCode(destinationAccountCode.toString());
        transferFundsRequest.setTransferCode(transferCode);
        return adyenFundService.transferFunds(transferFundsRequest);
    }

    protected void uploadPassportToAdyen(MiraklShop shop) throws Exception {
        URL url = Resources.getResource("adyenRequests/PassportDocumentContent.txt");
        UploadDocumentRequest uploadDocumentRequest = new UploadDocumentRequest();
        uploadDocumentRequest.setDocumentContent(Resources.toString(url, Charsets.UTF_8));
        DocumentDetail documentDetail = new DocumentDetail();
        documentDetail.setAccountHolderCode(shop.getId());
        documentDetail.setDescription("PASSED");
        documentDetail.setDocumentType(DocumentDetail.DocumentTypeEnum.valueOf("PASSPORT"));
        documentDetail.setFilename("passport.jpg");
        uploadDocumentRequest.setDocumentDetail(documentDetail);
        UploadDocumentResponse response = adyenAccountService.uploadDocument(uploadDocumentRequest);

        Assertions.assertThat(response.getAccountHolderCode()).isEqualTo(shop.getId());
    }

    protected void transferAccountHolderBalance(List<Map<String, String>> cucumberTable, MiraklShop shop) throws Exception {
        Long transferAmount = Long.valueOf(cucumberTable.get(0).get("transfer amount"));
        GetAccountHolderResponse accountHolder = getGetAccountHolderResponse(shop);
        transferAmountAndAssert(transferAmount, accountHolder);

        await().untilAsserted(() -> {
            AccountHolderBalanceRequest accountHolderBalanceRequest = new AccountHolderBalanceRequest();
            accountHolderBalanceRequest.setAccountHolderCode(shop.getId());
            AccountHolderBalanceResponse balance = adyenFundService.AccountHolderBalance(accountHolderBalanceRequest);

            Assertions
                .assertThat(balance.getTotalBalance().getBalance()
                    .stream()
                    .map(Amount::getValue)
                    .findAny().orElse(null))
                .isEqualTo(transferAmount);
        });
        log.info(String.format("\nAmount transferred successfully to [%s]", shop.getId()));
    }

    protected void transferAccountHolderBalanceFromAZeroBalanceAccount(List<Map<String, String>> cucumberTable, MiraklShop shop) {
        try {
            Long transferAmount = Long.valueOf(cucumberTable.get(0).get("transfer amount"));
            GetAccountHolderResponse accountHolder = getGetAccountHolderResponse(shop);
            transferAmountFromZeroBalanceAccount(transferAmount, accountHolder);
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
        transferAmountAndAssert(transferAmount, accountHolder);

        await().untilAsserted(() -> {
            AccountHolderBalanceRequest accountHolderBalanceRequest = new AccountHolderBalanceRequest();
            accountHolderBalanceRequest.setAccountHolderCode(shop.getId());
            AccountHolderBalanceResponse balance = adyenFundService.AccountHolderBalance(accountHolderBalanceRequest);

            Assertions
                .assertThat(balance.getTotalBalance().getBalance()
                    .stream()
                    .map(Amount::getValue)
                    .findAny().orElse(null))
                .isGreaterThan(transferAmount);
        });
        log.info(String.format("\nAmount transferred successfully to [%s]", shop.getId()));
    }

    private void transferAmountAndAssert(Long transferAmount, GetAccountHolderResponse accountHolder) {
        accountHolder.getAccounts().stream()
            .map(com.adyen.model.marketpay.Account::getAccountCode)
            .findAny()
            .ifPresent(accountCode -> {
                Integer destinationAccountCode = Integer.valueOf(accountCode);
                Integer sourceAccountCode = adyenAccountConfiguration.getAccountCode().get("sourceAccountCode");

                TransferFundsResponse response = null;
                try {
                    response = transferFundsAndRetrieveResponse(transferAmount, sourceAccountCode, destinationAccountCode);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                assert response != null;
                Assertions
                    .assertThat(response.getResultCode())
                    .isEqualTo("Received");
            });
    }

    private void transferAmountFromZeroBalanceAccount(Long transferAmount, GetAccountHolderResponse accountHolder) {
        accountHolder.getAccounts().stream()
            .map(com.adyen.model.marketpay.Account::getAccountCode)
            .findAny()
            .ifPresent(accountCode -> {
                Integer destinationAccountCode = Integer.valueOf(accountCode);
                Integer sourceAccountCode = adyenAccountConfiguration.getAccountCode().get("zeroBalanceSourceAccountCode");

                try {
                    transferFundsAndRetrieveResponse(transferAmount, sourceAccountCode, destinationAccountCode);
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

            Assertions.assertThat(parsedBody.title()).isEqualTo(title);
        });
    }

    protected void validationCheckOnReceivedEmails(String title, MiraklShop shop) throws Exception {
        GetAccountHolderResponse accountHolder = retrieveAccountHolderResponse(shop.getId());

        List<String> uboEmails = accountHolder.getAccountHolderDetails().getBusinessDetails().getShareholders().stream()
            .map(ShareholderContact::getEmail)
            .collect(Collectors.toList());

        await().with().pollInterval(fibonacci()).untilAsserted(() -> {
                ResponseBody responseBody = RestAssured.get(mailTrapConfiguration.mailTrapEndPoint()).thenReturn().body();
                final String response = responseBody.asString();
                if (response.equalsIgnoreCase("{\"error\":\"Throttled\"}")) {
                    log.warn("Mail throttled, will try again");
                }
                Assertions.assertThat(response).isNotEqualToIgnoringCase("{\"error\":\"Throttled\"}");

                List<Map<String, Object>> emails = responseBody.jsonPath().getList("");
                Assertions.assertThat(emails).size().isGreaterThan(0);

                boolean foundEmail = emails.stream()
                    .anyMatch(map -> map.get("to_email").equals(uboEmails.iterator().next()));
                Assertions.assertThat(foundEmail).isTrue();

                List<String> htmlBody = new LinkedList<>();
                for (String uboEmail : uboEmails) {
                    emails.stream()
                        .filter(map -> map.get("to_email").equals(uboEmail))
                        .findAny()
                        .ifPresent(map -> htmlBody.add(map.get("html_body").toString()));
                }
                Assertions.assertThat(htmlBody).isNotEmpty();
                Assertions.assertThat(htmlBody).hasSize(uboEmails.size());

                for (String body : htmlBody) {
                    Document parsedBody = Jsoup.parse(body);
                    Assertions
                        .assertThat(parsedBody.body().text())
                        .contains(shop.getId());
                    Assertions.assertThat(parsedBody.title()).isEqualTo(title);
                }
            }
        );
    }

    protected ImmutableList<DocumentContext> assertOnMultipleVerificationNotifications(String eventType, String verificationType, String verificationStatus, MiraklShop shop) throws Exception {
        waitForNotification();
        // get shareholderCodes from Adyen
        GetAccountHolderResponse accountHolder = getGetAccountHolderResponse(shop);

        List<String> shareholderCodes = accountHolder.getAccountHolderDetails().getBusinessDetails().getShareholders().stream()
            .map(ShareholderContact::getShareholderCode)
            .collect(Collectors.toList());

        log.info("Shareholders found: [{}]", shareholderCodes.size());
        // get all ACCOUNT_HOLDER_VERIFICATION notifications
        AtomicReference<ImmutableList<DocumentContext>> atomicReference = new AtomicReference<>();
        await().untilAsserted(() -> {
            List<DocumentContext> notifications = restAssuredAdyenApi
                .getMultipleAdyenNotificationBodies(startUpTestingHook.getBaseRequestBinUrlPath(), shop.getId(), eventType, verificationType);
            ImmutableList<DocumentContext> verificationNotifications = restAssuredAdyenApi.extractShareHolderNotifications(notifications, shareholderCodes);
            Assertions
                .assertThat(verificationNotifications)
                .withFailMessage("Notification is empty.")
                .isNotEmpty();

            Assertions
                .assertThat(verificationNotifications.size())
                .withFailMessage("Correct number of notifications were not found. Found: <%s>", verificationNotifications.size())
                .isEqualTo(shareholderCodes.size());

            verificationNotifications.forEach(notification -> Assertions
                .assertThat(notification.read("content.verificationStatus").toString())
                .isEqualTo(verificationStatus));
            atomicReference.set(verificationNotifications);
        });
        return atomicReference.get();
    }

    protected DocumentContext retrieveAndExtractTransferNotifications(String eventType, String status, String sourceAccountCode, String destinationAccountCode, String transferCode) {
        AtomicReference<DocumentContext> atomicDocContext = new AtomicReference<>();
        await().untilAsserted(() -> {
            DocumentContext transferNotification = null;
            ImmutableList<DocumentContext> notificationBodies = restAssuredAdyenApi
                .getMultipleAdyenTransferNotifications(startUpCucumberHook.getBaseRequestBinUrlPath(), eventType, transferCode);
            Assertions.assertThat(notificationBodies).isNotEmpty();

            if (notificationBodies.size() > 1){
                for (DocumentContext notification : notificationBodies) {
                    transferNotification = restAssuredAdyenApi
                        .extractCorrectTransferNotification(notification, sourceAccountCode, destinationAccountCode);
                    if (transferNotification != null) {
                        break;
                    }
                }
            } else {
                transferNotification = notificationBodies.get(0);
            }
            Assertions.assertThat(transferNotification).isNotNull();
            Assertions
                .assertThat(transferNotification.read("content.status.statusCode").toString())
                .isEqualTo(status);
            atomicDocContext.set(transferNotification);
        });
        return atomicDocContext.get();
    }

    protected String retrieveAdyenAccountCode(MiraklShop shop) throws Exception {
        GetAccountHolderResponse response = getGetAccountHolderResponse(shop);
        return response.getAccounts().stream()
            .map(com.adyen.model.marketpay.Account::getAccountCode)
            .findAny()
            .orElse(null);
    }
}
