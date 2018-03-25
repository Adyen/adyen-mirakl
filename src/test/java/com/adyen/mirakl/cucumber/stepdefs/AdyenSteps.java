package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.adyen.model.Amount;
import com.adyen.model.marketpay.*;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.jayway.jsonpath.JsonPath;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import cucumber.api.DataTable;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;

public class AdyenSteps extends StepDefsHelper {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private GetUploadedDocumentsResponse uploadedDocuments;

    @Then("^the documents are successfully uploaded to Adyen$")
    public void theDocumentsAreSuccessfullyUploadedToAdyen(DataTable table) throws Exception {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);

        GetUploadedDocumentsRequest getUploadedDocumentsRequest = new GetUploadedDocumentsRequest();
        getUploadedDocumentsRequest.setAccountHolderCode(world.miraklShop.getId());
        uploadedDocuments = adyenAccountService.getUploadedDocuments(getUploadedDocumentsRequest);

        ArrayList<DocumentDetail> documentDetails = new ArrayList<>(uploadedDocuments.getDocumentDetails());

        for (Map<String, String> stringStringMap : cucumberTable) {
            String documentType = stringStringMap.get("documentType");
            String filename = stringStringMap.get("filename");
            boolean fileMatch = documentDetails.stream()
                .anyMatch(detail ->
                    documentType.equals(DocumentDetail.DocumentTypeEnum.valueOf(documentType).toString())
                        && detail.getFilename().equals(filename));
            Assertions
                .assertThat(fileMatch)
                .withFailMessage(String.format("Document upload response:[%s]", JsonPath.parse(uploadedDocuments).toString()))
                .isTrue();
        }
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

    @Then("^the updated documents are successfully uploaded to Adyen$")
    public void theUpdatedDocumentsAreSuccessfullyUploadedToAdyen(DataTable table) throws Exception {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        for (Map<String, String> row : cucumberTable) {
            String documentType = row.get("documentType");
            String filename = row.get("filename");
            GetUploadedDocumentsRequest getUploadedDocumentsRequest = new GetUploadedDocumentsRequest();
            getUploadedDocumentsRequest.setAccountHolderCode(world.miraklShop.getId());
            uploadedDocuments = adyenAccountService.getUploadedDocuments(getUploadedDocumentsRequest);

            List<DocumentDetail> documents = uploadedDocuments.getDocumentDetails().stream()
                .filter(doc ->
                    DocumentDetail.DocumentTypeEnum.valueOf(documentType).equals(doc.getDocumentType())
                        && filename.equals(doc.getFilename()))
                .collect(Collectors.toList());

            Assertions
                .assertThat(documents)
                .hasSize(2);
        }
    }

    @And("^a passport has been uploaded to Adyen$")
    public void aPassportHasBeenUploadedToAdyen() throws Throwable {
        URL url = Resources.getResource("adyenRequests/PassportDocumentContent.txt");
        UploadDocumentRequest uploadDocumentRequest = new UploadDocumentRequest();
        uploadDocumentRequest.setDocumentContent(Resources.toString(url, Charsets.UTF_8));
        DocumentDetail documentDetail = new DocumentDetail();
        documentDetail.setAccountHolderCode(world.miraklShop.getId());
        documentDetail.setDescription("PASSED");
        documentDetail.setDocumentType(DocumentDetail.DocumentTypeEnum.valueOf("PASSPORT"));
        documentDetail.setFilename("passport.jpg");
        uploadDocumentRequest.setDocumentDetail(documentDetail);
        UploadDocumentResponse response = adyenAccountService.uploadDocument(uploadDocumentRequest);

        Assertions.assertThat(response.getAccountHolderCode()).isEqualTo(world.miraklShop.getId());
    }


    @And("^getAccountHolder will have the correct amount of shareholders and data in Adyen$")
    public void getaccountholderWillHaveTheCorrectAmountOfShareholdersAndDataInAdyen(DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        String maxUbos = cucumberTable.get(0).get("maxUbos");

        GetAccountHolderResponse accountHolder = getGetAccountHolderResponse(world.miraklShop);

        BusinessDetails businessDetails = new BusinessDetails();
        List<ShareholderContact> shareholders = businessDetails.getShareholders();
        accountHolder.getAccountHolderDetails().businessDetails(businessDetails);

        for (ShareholderContact contact : shareholders) {
            Assertions
                .assertThat(world.miraklShop.getContactInformation().getFirstname())
                .isEqualTo(contact.getName().getFirstName());
            Assertions
                .assertThat(world.miraklShop.getContactInformation().getLastname())
                .isEqualTo(contact.getName().getLastName());
            Assertions
                .assertThat(world.miraklShop.getContactInformation().getEmail())
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
        getUploadedDocumentsRequest.setAccountHolderCode(world.miraklShop.getId());
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

    @And("^an AccountHolder will be created in Adyen with status Active$")
    public void anAccountHolderWillBeCreatedInAdyenWithStatusActive() throws Throwable {
        GetAccountHolderRequest accountHolderRequest = new GetAccountHolderRequest();
        accountHolderRequest.setAccountHolderCode(world.miraklShop.getId());
        GetAccountHolderResponse accountHolderResponse = adyenAccountService.getAccountHolder(accountHolderRequest);
        Assertions.assertThat(accountHolderResponse.getAccountHolderStatus().getStatus().toString()).isEqualTo("Active");
    }

    @When("^the accountHolders balance is increased$")
    public void theAccountHoldersBalanceIsIncreased(DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        Long transferAmount = Long.valueOf(cucumberTable.get(0).get("transfer amount"));
        String sourceAccountHolderCode = cucumberTable.get(0).get("source accountHolderCode");

        MiraklShop miraklShop = world.miraklShop;
        GetAccountHolderResponse accountHolder = getGetAccountHolderResponse(miraklShop);

        accountHolder.getAccounts().stream()
            .map(Account::getAccountCode)
            .findAny()
            .ifPresent(accountCode -> {
                Integer destinationAccountCode = Integer.valueOf(accountCode);
                Integer sourceAccountCode = adyenAccountConfiguration.getAccountCode().get(sourceAccountHolderCode);

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

        await().untilAsserted(() -> {
            AccountHolderBalanceRequest accountHolderBalanceRequest = new AccountHolderBalanceRequest();
            accountHolderBalanceRequest.setAccountHolderCode(miraklShop.getId());
            AccountHolderBalanceResponse balance = adyenFundService.AccountHolderBalance(accountHolderBalanceRequest);

            Assertions
                .assertThat(balance.getTotalBalance().getBalance()
                    .stream()
                    .map(Amount::getValue)
                    .findAny().orElse(null))
                .isEqualTo(transferAmount);

            GetAccountHolderResponse account = getGetAccountHolderResponse(miraklShop);

            Assertions
                .assertThat(account.getAccountHolderStatus().getPayoutState().getAllowPayout())
                .isTrue();
        });
        log.info("Amount transferred successfully.");
    }
}
