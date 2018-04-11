package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.adyen.mirakl.web.rest.AdyenNotificationResource;
import com.adyen.mirakl.web.rest.TestUtil;
import com.adyen.model.marketpay.*;
import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.document.MiraklShopDocument;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import com.mirakl.client.mmp.request.shop.document.MiraklGetShopDocumentsRequest;
import cucumber.api.DataTable;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import net.minidev.json.JSONArray;
import org.assertj.core.api.Assertions;
import org.awaitility.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class IdentityVerificationSteps extends StepDefsHelper {

    @Autowired
    private AdyenNotificationResource adyenNotificationResource;
    private MockMvc restAdyenNotificationMockMvc;
    private MiraklShop shop;
    private GetUploadedDocumentsResponse uploadedDocuments;
    private ImmutableList<DocumentContext> notifications;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Before
    public void mockMvcSetup() {
        this.restAdyenNotificationMockMvc = MockMvcBuilders.standaloneSetup(adyenNotificationResource).build();
    }

    @Given("^a shop has been created with full UBO data for a (.*)")
    public void aNewShopHasBeenCreatedAsABusinessWithUBOData(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithFullUboInfo(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        this.shop = retrieveCreatedShop(shops);
    }

    @Given("^a seller creates a shop as a (.*) without providing UBO mandatory data$")
    public void waNewBusinessShopHasBeenCreatedInMiraklWithoutMandatoryShareholderInformation(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithMissingUboInfo(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        this.shop = retrieveCreatedShop(shops);
    }

    @When("^the shareholder data has been updated in Mirakl$")
    public void theShareholderDataHasBeenUpdatedInMirakl(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        this.shop = miraklUpdateShopApi
            .updateUboData(shop, shop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }

    @Then("^adyen will send multiple (.*) notifications with (.*) of status (.*)$")
    public void adyenWillSendMultipleACCOUNT_HOLDER_VERIFICATIONNotificationWithIDENTITY_VERIFICATIONOfStatusDATA_PROVIDED(String eventType, String verificationType, String verificationStatus) throws Exception {
        notifications = assertOnMultipleVerificationNotifications(eventType, verificationType, verificationStatus, shop);
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

    @And("^getAccountHolder will have the correct amount of shareholders and data in Adyen$")
    public void getaccountholderWillHaveTheCorrectAmountOfShareholdersAndDataInAdyen(DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        String maxUbos = cucumberTable.get(0).get("maxUbos");
        GetAccountHolderResponse accountHolder = getGetAccountHolderResponse(this.shop);
        BusinessDetails businessDetails = new BusinessDetails();
        List<ShareholderContact> shareholders = businessDetails.getShareholders();
        accountHolder.getAccountHolderDetails().businessDetails(businessDetails);
        for (ShareholderContact contact : shareholders) {
            Assertions.assertThat(shop.getContactInformation().getFirstname()).isEqualTo(contact.getName().getFirstName());
            Assertions.assertThat(shop.getContactInformation().getLastname()).isEqualTo(contact.getName().getLastName());
            Assertions.assertThat(shop.getContactInformation().getEmail()).isEqualTo(contact.getEmail());
            Assertions.assertThat(shareholders.size()).isEqualTo(Integer.valueOf(maxUbos));
        }
    }

    @When("^the seller uploads a document in Mirakl$")
    public void theSellerUploadsADocumentInMirakl(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        miraklUpdateShopApi.uploadIdentityDocumentToExistingShop(shop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }

    @And("^sets the photoIdType in Mirakl$")
    public void setsThePhotoidtypeToPASSPORT(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        miraklUpdateShopApi.updateShopWithPhotoIdForShareHolder(shop, shop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }

    @Then("^the documents are successfully uploaded to Adyen$")
    public void theDocumentsAreSuccessfullyUploadedToAdyen(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);

        await().atMost(Duration.ONE_MINUTE).untilAsserted(() -> {
            GetUploadedDocumentsRequest getUploadedDocumentsRequest = new GetUploadedDocumentsRequest();
            getUploadedDocumentsRequest.setAccountHolderCode(shop.getId());
            uploadedDocuments = adyenAccountService.getUploadedDocuments(getUploadedDocumentsRequest);
            ArrayList<DocumentDetail> documentDetails = new ArrayList<>(uploadedDocuments.getDocumentDetails());
            for (Map<String, String> stringStringMap : cucumberTable) {
                String documentType = stringStringMap.get("documentType");
                String filename = stringStringMap.get("filename");
                boolean fileMatch = documentDetails.stream()
                    .anyMatch(detail ->
                        documentType.equals(DocumentDetail.DocumentTypeEnum.valueOf(documentType).toString())
                            && detail.getFilename().equals(filename));
                Assertions.assertThat(fileMatch)
                    .withFailMessage("Found the following docs: <%s>", documentDetails.toString())
                    .isTrue();
            }
        });
    }

    @And("^the following document will not be uploaded to Adyen$")
    public void theFollowingDocumentWillNotBeUploadedToAdyen(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        cucumberTable.forEach(row -> {
            String documentType = row.get("documentType");
            String filename = row.get("filename");
            boolean documentTypeAndFilenameMatch = uploadedDocuments.getDocumentDetails().stream()
                .anyMatch(doc ->
                    DocumentDetail.DocumentTypeEnum.valueOf(documentType).equals(doc.getDocumentType())
                        && filename.equals(doc.getFilename())
                );
            Assertions
                .assertThat(documentTypeAndFilenameMatch)
                .withFailMessage(String.format("Document upload response:[%s]", JsonPath.parse(uploadedDocuments).toString()))
                .isFalse();
        });
    }

    @And("^the ACCOUNT_HOLDER_VERIFICATION notifications are sent to Connector App$")
    public void theACCOUNT_HOLDER_VERIFICATIONNotificationsAreSentToConnectorApp() throws Exception {
        for (DocumentContext notification : notifications) {
            restAdyenNotificationMockMvc.perform(post("/api/adyen-notifications")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(notification.jsonString()))
                .andExpect(status().is(201));
            log.info("Notification posted to Connector: [{}]", notification.jsonString());
        }
    }

    @Then("^the updated documents are successfully uploaded to Adyen$")
    public void theUpdatedDocumentsAreSuccessfullyUploadedToAdyen(DataTable table) throws Exception {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        for (Map<String, String> row : cucumberTable) {
            String documentType = row.get("documentType");
            String filename = row.get("filename");
            GetUploadedDocumentsRequest getUploadedDocumentsRequest = new GetUploadedDocumentsRequest();
            getUploadedDocumentsRequest.setAccountHolderCode(shop.getId());
            uploadedDocuments = adyenAccountService.getUploadedDocuments(getUploadedDocumentsRequest);
            List<DocumentDetail> documents = uploadedDocuments.getDocumentDetails().stream()
                .filter(doc ->
                    DocumentDetail.DocumentTypeEnum.valueOf(documentType).equals(doc.getDocumentType())
                        && filename.equals(doc.getFilename()))
                .collect(Collectors.toList());
            Assertions.assertThat(documents).hasSize(2);
        }
    }

    @Then("^the documents will be removed for each of the UBOs$")
    public void theDocumentsWillBeRemovedForEachOfTheUBOs() {
        await().atMost(Duration.TEN_SECONDS).untilAsserted(() -> {
            MiraklGetShopDocumentsRequest request = new MiraklGetShopDocumentsRequest(ImmutableList.of(shop.getId()));
            List<MiraklShopDocument> shopDocuments = miraklMarketplacePlatformOperatorApiClient.getShopDocuments(request);
            Assertions.assertThat(shopDocuments).isEmpty();
        });
    }

    @Then("^each UBO will receive a remedial email$")
    public void eachUBOWillReceiveARemedialEmail(String title) throws Throwable {
        validationCheckOnReceivedEmails(title, shop);
    }
}
