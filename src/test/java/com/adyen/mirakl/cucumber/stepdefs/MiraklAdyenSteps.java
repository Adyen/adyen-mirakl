package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.adyen.model.marketpay.*;
import com.adyen.service.exception.ApiException;
import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import cucumber.api.DataTable;
import cucumber.api.Scenario;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.ResponseBody;
import net.minidev.json.JSONArray;
import org.assertj.core.api.Assertions;
import org.awaitility.Duration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.fail;
import static org.awaitility.Awaitility.await;

public class MiraklAdyenSteps extends StepDefsHelper {

    private MiraklShop shop;
    private DocumentContext notificationResponse;
    private String accountHolderCode;
    private String accountCode;
    private Scenario scenario;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Before
    public void getScenario(Scenario scenario) {
        this.scenario = scenario;
    }

    @Given("^a shop has been created in Mirakl for an (.*) with Bank Information$")
    public void aShopHasBeenCreatedInMiraklForAnIndividualWithBankInformation(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi
            .createShopForIndividualWithBankDetails(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);

        // only used for 1 scenario (ADY-23)
        if (scenario.getSourceTagNames().contains("@ADY-23")) {
         cucumberMap.put("createdShop", shop);
        }
    }

    @Given("^a new shop has been created in Mirakl for an (.*)$")
    public void aNewShopHasBeenCreatedInMiraklForAnIndividual(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createShopForIndividual(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @Given("^a shop has been created in Mirakl for an (.*) with mandatory KYC data$")
    public void aShopHasBeenCreatedInMiraklForAnIndividualWithMandatoryKYCData(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createShopForIndividualWithBankDetails(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @When("^a new shop has been created in Mirakl for a (.*)")
    public void aNewShopHasBeenCreatedInMiraklForABusiness(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithNoUBOs(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @When("^a new shop has been created in Mirakl with UBO Data for a (.*)")
    public void aNewShopHasBeenCreatedAsABusinessWithUBOData(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithFullUboInfo(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @Given("^a new (.*) shop has been created in Mirakl without mandatory Shareholder Information$")
    public void aNewBusinessShopHasBeenCreatedInMiraklWithoutMandatoryShareholderInformation(String legalEntity, DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithMissingUboInfo(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        shop = retrieveCreatedShop(shops);
    }

    @When("^the seller uploads a Bank Statement in Mirakl$")
    public void theSellerUploadsABankStatementInMirakl() {
        miraklUpdateShopApi
            .uploadBankStatementToExistingShop(shop.getId(), miraklMarketplacePlatformOperatorApiClient);
    }

    @When("^the IBAN has been modified in Mirakl$")
    public void theIBANHasBeenModifiedInMirakl(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        shop = miraklUpdateShopApi
            .updateShopsIbanNumberOnly(shop, shop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }

    @And("^a new IBAN has been provided by the seller in Mirakl and the mandatory IBAN fields have been provided$")
    public void aNewIBANHasBeenProvidedByTheSellerInMiraklAndTheMandatoryIBANFieldsHaveBeenProvided() {
        shop = miraklUpdateShopApi
            .updateShopToAddBankDetails(shop, shop.getId(), miraklMarketplacePlatformOperatorApiClient);
    }

    @And("^Mirakl has been updated with a taxId$")
    public void miraklHasBeenUpdatedWithATaxId() {
        shop = miraklUpdateShopApi
            .updateShopToIncludeVATNumber(shop, shop.getId(), miraklMarketplacePlatformOperatorApiClient);
    }

    @When("^we update the shop by adding more shareholder data$")
    public void weUpdateTheShopByAddingMoreShareholderData(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        shop = miraklUpdateShopApi
            .addMoreUbosToShop(shop, shop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }

    @When("^the shareholder data has been updated in Mirakl$")
    public void theShareholderDataHasBeenUpdatedInMirakl(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        shop = miraklUpdateShopApi
            .updateUboData(shop, shop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }

    @Then("^the (.*) notification is sent by Adyen comprising of (.*) and (.*)")
    public void theACCOUNT_HOLDER_VERIFICATIONNotificationIsSentByAdyenComprisingOfBANK_ACCOUNT_VERIFICATIONAndPASSED(String notification,
                                                                                                                      String verificationType,
                                                                                                                      String verificationStatus) {
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> adyenNotificationBody = restAssuredAdyenApi
                .getAdyenNotificationBody(startUpTestingHook.getBaseRequestBinUrlPath(), shop.getId(), notification, verificationType);

            Assertions.assertThat(adyenNotificationBody).withFailMessage("No data received from notification endpoint").isNotNull();
            Assertions.assertThat(JsonPath.parse(adyenNotificationBody.get("content"))
                .read("verificationStatus").toString()).isEqualTo(verificationStatus);
            Assertions.assertThat(JsonPath.parse(adyenNotificationBody.get("content"))
                .read("verificationType").toString()).isEqualTo(verificationType);
        });
    }

    @Then("^a new bankAccountDetail will be created for the existing Account Holder$")
    public void aNewBankAccountDetailWillBeCreatedForTheExistingAccountHolder(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        waitForNotification();
        await().untilAsserted(() -> {
            String eventType = cucumberTable.get(0).get("eventType");
            Map<String, Object> adyenNotificationBody = retrieveAdyenNotificationBody(eventType, shop.getId());

            List<Map<Object, Object>> bankAccountDetails = JsonPath.parse(adyenNotificationBody
                .get("content"))
                .read("accountHolderDetails.bankAccountDetails");

            ImmutableList<String> miraklBankAccountDetail = assertionHelper.miraklBankAccountInformation(shop).build();
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

    @And("^the previous BankAccountDetail will be removed$")
    public void thePreviousBankAccountDetailWillBeRemoved(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        String eventType = cucumberTable.get(0).get("eventType");
        String reason = cucumberTable.get(0).get("reason");

        await().untilAsserted(() -> {
            DocumentContext adyenNotificationBody = JsonPath.parse(retrieveAdyenNotificationBody(eventType, shop.getId()));
            Assertions.assertThat(adyenNotificationBody.read("content.reason").toString())
                .isEqualTo(reason);
        });
    }

    @Then("^adyen will send the (.*) comprising of (\\w*) and status of (.*)")
    public void adyenWillSendTheACCOUNT_HOLDER_VERIFICATIONComprisingOfCOMPANY_VERIFICATION(String eventType, String verificationType, String status) {
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> adyenNotificationBody = restAssuredAdyenApi
                .getAdyenNotificationBody(startUpTestingHook.getBaseRequestBinUrlPath(), shop.getId(), eventType, verificationType);
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
                .getAdyenNotificationBody(startUpTestingHook.getBaseRequestBinUrlPath(), shop.getId(), eventType, verificationType);
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
                .getAdyenNotificationBody(startUpCucumberHook.getBaseRequestBinUrlPath(), shop.getId(), eventType, verificationType);

            Assertions.assertThat(adyenNotificationBody).isNotEmpty();
            JSONArray shareholderJsonArray = JsonPath.parse(adyenNotificationBody).read("content.verification.shareholders.*");
            for (Object shareholder : shareholderJsonArray) {
                Object checks = JsonPath.parse(shareholder).read("checks[0]");
                Assertions.assertThat(JsonPath.parse(checks).read("type").toString()).isEqualTo(verificationType);
                Assertions.assertThat(JsonPath.parse(checks).read("status").toString()).isEqualTo(verificationStatus);
            }
        });
    }

    @Then("^adyen will send multiple (.*) notification with (.*) of status (.*)$")
    public void adyenWillSendMultipleACCOUNT_HOLDER_VERIFICATIONNotificationWithIDENTITY_VERIFICATIONOfStatusDATA_PROVIDED(
        String eventType, String verificationType, String verificationStatus) {
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> accountHolderCreated = restAssuredAdyenApi.getAdyenNotificationBody(
                startUpCucumberHook.getBaseRequestBinUrlPath(), shop.getId(), eventType, verificationType);
            Assertions.assertThat(accountHolderCreated).isNotEmpty();

            ArrayList shareholdersArray = JsonPath.parse(accountHolderCreated.get("content")).read("verification.shareholders");
            for (Object shareholder : shareholdersArray) {
                ArrayList checks = JsonPath.parse(shareholder).read("checks.*");
                Assertions
                    .assertThat(JsonPath.parse(checks).read("[0]status").toString())
                    .isEqualTo(verificationStatus);
            }
        });
    }

    @And("^getAccountHolder will have the correct amount of shareholders and data in Adyen$")
    public void getaccountholderWillHaveTheCorrectAmountOfShareholdersAndDataInAdyen(DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        String maxUbos = cucumberTable.get(0).get("maxUbos");

        GetAccountHolderRequest getAccountHolderRequest = new GetAccountHolderRequest();
        getAccountHolderRequest.setAccountHolderCode(shop.getId());
        GetAccountHolderResponse accountHolder = adyenAccountService.getAccountHolder(getAccountHolderRequest);

        BusinessDetails businessDetails = new BusinessDetails();
        List<ShareholderContact> shareholders = businessDetails.getShareholders();
        accountHolder.getAccountHolderDetails().businessDetails(businessDetails);

        for (ShareholderContact contact : shareholders) {
            Assertions
                .assertThat(shop.getContactInformation().getFirstname())
                .isEqualTo(contact.getName().getFirstName());
            Assertions
                .assertThat(shop.getContactInformation().getLastname())
                .isEqualTo(contact.getName().getLastName());
            Assertions
                .assertThat(shop.getContactInformation().getEmail())
                .isEqualTo(contact.getEmail());
            Assertions
                .assertThat(shareholders.size())
                .isEqualTo(Integer.valueOf(maxUbos));
        }
    }

    @And("^the document is successfully uploaded to Adyen$")
    public void theDocumentIsSuccessfullyUploadedToAdyen(DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);

        GetUploadedDocumentsRequest getUploadedDocumentsRequest = new GetUploadedDocumentsRequest();
        getUploadedDocumentsRequest.setAccountHolderCode(shop.getId());
        GetUploadedDocumentsResponse uploadedDocuments = adyenAccountService.getUploadedDocuments(getUploadedDocumentsRequest);

        boolean documentTypeAndFilenameMatch = uploadedDocuments.getDocumentDetails().stream()
            .anyMatch(doc ->
                DocumentDetail.DocumentTypeEnum.valueOf(cucumberTable.get(0).get("documentType")).equals(doc.getDocumentType())
                    && cucumberTable.get(0).get("filename").equals(doc.getFilename()));

        String uploadedDocResponse = uploadedDocuments.getDocumentDetails().toString();
        Assertions.assertThat(documentTypeAndFilenameMatch)
            .withFailMessage(String.format("Document upload response:[%s]", JsonPath.parse(uploadedDocResponse).toString()))
            .isTrue();
    }

    @Then("^an email will be sent to the seller$")
    public void anEmailWillBeSentToTheSeller() {
        String email = shop.getContactInformation().getEmail();

        await().atMost(Duration.ONE_MINUTE).untilAsserted(() -> {
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

    @Then("^adyen will send the (.*) notification with status$")
    public void adyenWillSendTheACCOUNT_HOLDER_PAYOUTNotificationWithStatusCode(String notification, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        waitForNotification();
        Map<String, Object> adyenNotificationBody = retrieveAdyenNotificationBody(notification, accountHolderCode);
        DocumentContext content = JsonPath.parse(adyenNotificationBody.get("content"));
        Assertions.assertThat(cucumberTable.get(0).get("statusCode"))
            .withFailMessage("Status was not correct.")
            .isEqualTo(content.read("status.statusCode"));

        String message = cucumberTable.get(0).get("message");
        String messageWithAccountCode = String.format("%s %s", message, accountCode);

        Assertions.assertThat(content.read("status.message ").toString())
            .contains(messageWithAccountCode);
    }

    @And("^an AccountHolder will be created in Adyen with status Active$")
    public void anAccountHolderWillBeCreatedInAdyenWithStatusActive() throws Throwable {
        GetAccountHolderRequest accountHolderRequest = new GetAccountHolderRequest();
        accountHolderRequest.setAccountHolderCode(shop.getId());

        try {
            GetAccountHolderResponse accountHolderResponse = adyenAccountService.getAccountHolder(accountHolderRequest);
            Assertions.assertThat(accountHolderResponse.getAccountHolderStatus().getStatus().toString()).isEqualTo("Active");
        } catch (ApiException e) {
            log.error("Failing test due to exception", e);
            fail(e.getError().toString());
        }
    }

    @And("^a notification will be sent pertaining to (.*)$")
    public void aNotificationWillBeSentPertainingToACCOUNT_HOLDER_CREATED(String notification) {
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> mappedAdyenNotificationResponse = retrieveAdyenNotificationBody(notification, shop.getId());
            Assertions.assertThat(mappedAdyenNotificationResponse).isNotNull();
            notificationResponse = JsonPath.parse(mappedAdyenNotificationResponse);
            Assertions.assertThat(notificationResponse.read("content.accountHolderCode").toString())
                .isEqualTo(shop.getId());
            Assertions.assertThat(notificationResponse.read("eventType").toString())
                .isEqualTo(notification);
        });
    }

    @Then("^no account holder is created in Adyen$")
    public void noAccountHolderIsCreatedInAdyen() {
        await().pollDelay(Duration.TEN_SECONDS).untilAsserted(() -> {
            Map mapResult = restAssuredAdyenApi.getAdyenNotificationBody(startUpCucumberHook
                .getBaseRequestBinUrlPath(), shop.getId(), "ACCOUNT_HOLDER_CREATED", null);
            Assertions.assertThat(mapResult == null);
        });
    }

    @And("^the shop data is correctly mapped to the Adyen Account$")
    public void theShopDataIsCorrectlyMappedToTheAdyenAccount() {
        ImmutableList<String> adyen = assertionHelper.adyenAccountDataBuilder(notificationResponse).build();
        ImmutableList<String> mirakl = assertionHelper.miraklShopDataBuilder(shop.getContactInformation().getEmail(), shop).build();
        Assertions.assertThat(adyen).containsAll(mirakl);
    }

    @And("^the account holder is created in Adyen with status Active$")
    public void theAccountHolderIsCreatedInAdyenWithStatusActive() {
        Assertions.assertThat(notificationResponse.read("content.accountHolderStatus.status").toString())
            .isEqualTo("Active");
    }

    @And("^the shop data is correctly mapped to the Adyen Business Account$")
    public void theShopDataIsCorrectlyMappedToTheAdyenBusinessAccount(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);

        ImmutableList<String> adyen = assertionHelper.adyenShareHolderAccountDataBuilder(notificationResponse).build();
        ImmutableList<String> mirakl = assertionHelper.miraklShopShareHolderDataBuilder(shop, cucumberTable).build();
        Assertions.assertThat(adyen).containsAll(mirakl);
    }

    @When("^the Mirakl Shop Details have been updated$")
    public void theMiraklShopDetailsHaveBeenUpdated(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        cucumberTable.forEach(row ->
            shop = miraklUpdateShopApi.updateExistingShopsContactInfoWithTableData(shop, shop.getId(), miraklMarketplacePlatformOperatorApiClient, row)
        );
    }

    @Test(enabled = false)
    @Given("^a shop exists in Mirakl$")
    public void updateShopExistsInMirakl(DataTable table) {
        List<Map<Object, Object>> rows = table.getTableConverter().toMaps(table, String.class, String.class);

        String seller = shopConfiguration.shopIds.get(rows.get(0).get("seller").toString()).toString();
        shop = getMiraklShop(miraklMarketplacePlatformOperatorApiClient, seller);
    }

    @Given("^a AccountHolder exists who (?:is not|is) eligible for payout$")
    public void aAccountHolderExistsWhoHasPassedKYCChecksAndIsEligibleForPayout(DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        String seller = cucumberTable.get(0).get("seller");
        accountHolderCode = shopConfiguration.shopIds.get(seller).toString();

        GetAccountHolderRequest request = new GetAccountHolderRequest();
        request.setAccountHolderCode(accountHolderCode);
        GetAccountHolderResponse response = adyenConfiguration.adyenAccountService().getAccountHolder(request);
        Boolean allowPayout = response.getAccountHolderStatus().getPayoutState().getAllowPayout();
        accountCode = response.getAccounts()
            .stream()
            .map(com.adyen.model.marketpay.Account::getAccountCode)
            .findFirst()
            .orElse(null);

        Assertions.assertThat(allowPayout)
            .withFailMessage("Payout status is not true for accountHolderCode: <%s> (seller: <%s>)", accountHolderCode, seller)
            .isEqualTo(Boolean.parseBoolean(cucumberTable.get(0).get("allowPayout")));
    }

    @Then("^adyen will send the (.*) notification$")
    public void adyenWillSendTheACCOUNT_HOLDER_PAYOUTNotification(String notification, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> adyenNotificationBody = retrieveAdyenNotificationBody(notification, accountHolderCode);
            DocumentContext content = JsonPath.parse(adyenNotificationBody.get("content"));
            cucumberTable.forEach(row -> {
                Assertions.assertThat(row.get("currency"))
                    .isEqualTo(content.read("amounts[0].Amount.currency"));

                Assertions.assertThat(row.get("amount"))
                    .isEqualTo(Double.toString(content.read("amounts[0].Amount.value")));

                Assertions.assertThat(row.get("iban"))
                    .isEqualTo(content.read("bankAccountDetail.iban"));

                Assertions.assertThat(row.get("statusCode"))
                    .isEqualTo(content.read("status.statusCode"));
            });
        });
    }

    @When("^the Mirakl Shop Details have been updated with invalid data$")
    public void theMiraklShopDetailsHaveBeenUpdatedWithInvalidData(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
            shop = miraklUpdateShopApi.updateUboDataWithInvalidData(shop, shop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }
}
