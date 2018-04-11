package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.adyen.model.marketpay.DocumentDetail;
import com.adyen.model.marketpay.GetUploadedDocumentsRequest;
import com.adyen.model.marketpay.GetUploadedDocumentsResponse;
import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import cucumber.api.DataTable;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;

public class BankAccountVerificationSteps extends StepDefsHelper{

    private MiraklShop shop;
    private List<DocumentContext> notifications;

    @Given("^a shop has been created in Mirakl for an (.*) with Bank Information$")
    public void aShopHasBeenCreatedInMiraklForAnIndividualWithBankInformation(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi
            .createShopForIndividualWithBankDetails(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        this.shop = retrieveCreatedShop(shops);
    }

    @Given("^a seller creates a shop as a (.*) without entering a bank account$")
    public void aSellerCreatesAShopAsAIndividualWithoutEnteringaBankAccount(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createShopForIndividual(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        this.shop = retrieveCreatedShop(shops);
    }

    @When("^the IBAN has been modified in Mirakl$")
    public void theIBANHasBeenModifiedInMirakl(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        this.shop = miraklUpdateShopApi
            .updateShopsIbanNumberOnly(this.shop, this.shop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }

    @And("^a new IBAN has been provided by the seller in Mirakl and the mandatory IBAN fields have been provided$")
    public void aNewIBANHasBeenProvidedByTheSellerInMiraklAndTheMandatoryIBANFieldsHaveBeenProvided() {
        this.shop = miraklUpdateShopApi
            .updateShopToAddBankDetails(this.shop, this.shop.getId(), miraklMarketplacePlatformOperatorApiClient);
    }

    @When("^the seller uploads a Bank Statement in Mirakl$")
    public void theSellerUploadsABankStatementInMirakl() {
        miraklUpdateShopApi
            .uploadBankStatementToExistingShop(shop.getId(), miraklMarketplacePlatformOperatorApiClient);
    }

    @Then("^the (.*) notification is sent by Adyen comprising of (.*) and (.*)")
    public void theACCOUNT_HOLDER_VERIFICATIONNotificationIsSentByAdyenComprisingOfBANK_ACCOUNT_VERIFICATIONAndPASSED(String notification,
                                                                                                                      String verificationType,
                                                                                                                      String verificationStatus) {
        waitForNotification();
        await().untilAsserted(() -> {
            Map<String, Object> adyenNotificationBody = restAssuredAdyenApi
                .getAdyenNotificationBody(startUpTestingHook.getBaseRequestBinUrlPath(), this.shop.getId(), notification, verificationType);
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
        waitForNotification();
        await().untilAsserted(() -> {
            this.notifications = restAssuredAdyenApi
                .getMultipleAdyenNotificationBodies(startUpTestingHook.getBaseRequestBinUrlPath(), shop.getId(), eventType, null);
            Assertions.assertThat(notifications).isNotEmpty();
            boolean foundReason = notifications.stream()
                .anyMatch(notification -> notification.read("content.reason").toString().contains(reason));
            Assertions.assertThat(foundReason).isTrue();
            for (DocumentContext notification : notifications) {
                String notificationReason = notification.read("content.reason").toString();
                if (notificationReason.contains(reason)) {
                    Assertions.assertThat(notificationReason.contains(reason));
                    break;
                }
            }
        });
    }

    @And("^the document is successfully uploaded to Adyen$")
    public void theDocumentIsSuccessfullyUploadedToAdyen(DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        GetUploadedDocumentsRequest getUploadedDocumentsRequest = new GetUploadedDocumentsRequest();
        getUploadedDocumentsRequest.setAccountHolderCode(this.shop.getId());
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
}
