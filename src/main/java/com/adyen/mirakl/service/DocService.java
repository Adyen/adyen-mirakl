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

package com.adyen.mirakl.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.adyen.enums.Environment;
import com.adyen.mirakl.config.ApplicationProperties;
import com.adyen.mirakl.config.Constants;
import com.adyen.mirakl.domain.DocError;
import com.adyen.mirakl.domain.DocRetry;
import com.adyen.mirakl.domain.ShareholderMapping;
import com.adyen.mirakl.repository.DocErrorRepository;
import com.adyen.mirakl.repository.DocRetryRepository;
import com.adyen.mirakl.repository.ShareholderMappingRepository;
import com.adyen.mirakl.service.dto.DocumentDTO;
import com.adyen.mirakl.service.dto.UboDocumentDTO;
import com.adyen.mirakl.service.util.GetShopDocumentsRequest;
import com.adyen.model.marketpay.DocumentDetail;
import com.adyen.model.marketpay.GetAccountHolderRequest;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.UploadDocumentRequest;
import com.adyen.model.marketpay.UploadDocumentResponse;
import com.adyen.service.Account;
import com.adyen.service.exception.ApiException;
import com.google.common.collect.ImmutableList;
import com.mirakl.client.mmp.domain.common.FileWrapper;
import com.mirakl.client.mmp.domain.shop.document.MiraklShopDocument;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.request.shop.document.MiraklDeleteShopDocumentRequest;
import com.mirakl.client.mmp.request.shop.document.MiraklDownloadShopsDocumentsRequest;
import com.mirakl.client.mmp.request.shop.document.MiraklGetShopDocumentsRequest;
import static com.google.common.io.Files.toByteArray;

@Service
public class DocService {

    private final Logger log = LoggerFactory.getLogger(DocService.class);

    private static final String UBO_ENTITY_TYPE = "ubo";
    private static final String INDIVIDUAL_ENTITY_TYPE = "individual";

    @Resource
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient;

    @Resource
    private Account adyenAccountService;

    @Resource
    private DeltaService deltaService;

    @Resource
    private IndividualDocumentService individualDocumentService;

    @Resource
    private UboDocumentService uboDocumentService;

    @Resource
    private ShareholderMappingRepository shareholderMappingRepository;

    @Resource
    private DocRetryRepository docRetryRepository;

    @Resource
    private DocErrorRepository docErrorRepository;

    @Resource
    private ApplicationProperties applicationProperties;

    @Value("${adyenConfig.environment}")
    private String environment;

    /**
     * Calling S30, S31, GetAccountHolder and UploadDocument to upload bankproof documents to Adyen
     */
    public void processUpdatedDocuments() {
        final ZonedDateTime beforeProcessing = ZonedDateTime.now();

        List<MiraklShopDocument> miraklShopDocumentList = retrieveUpdatedDocs();
        processDocs(miraklShopDocumentList);
        deltaService.updateDocumentDelta(beforeProcessing);
    }

    private void processDocs(final List<MiraklShopDocument> miraklShopDocumentList) {
        for (MiraklShopDocument document : miraklShopDocumentList) {
            if (Constants.BANKPROOF.equals(document.getTypeCode())) {
                updateDocument(document, DocumentDetail.DocumentTypeEnum.BANK_STATEMENT);
            }
            else if (Constants.COMPANY_REGISTRATION.equals(document.getTypeCode())) {
                updateDocument(document, DocumentDetail.DocumentTypeEnum.COMPANY_REGISTRATION_SCREENING);
            }
        }

        final List<UboDocumentDTO> uboDocumentDTOS = uboDocumentService.extractDocuments(miraklShopDocumentList);
        uboDocumentDTOS.forEach(documentDTO -> updateDocument(documentDTO.getMiraklShopDocument(), documentDTO.getDocumentTypeEnum(), documentDTO.getShareholderCode()));

        final List<DocumentDTO> individualDocumentDTOS = individualDocumentService.extractDocuments(miraklShopDocumentList);
        individualDocumentDTOS.forEach(documentDTO -> updateDocument(documentDTO.getMiraklShopDocument(), documentDTO.getDocumentTypeEnum(), null));
    }

    @Async
    public void retryDocumentsForShop(String shopId) {
        final List<DocRetry> retryDocsByShopId = docRetryRepository.findByShopId(shopId);
        if (! retryDocsByShopId.isEmpty()) {
            retryFailedDocuments(retryDocsByShopId);
        }
    }

    @Async
    public void retryFailedDocuments() {
        final List<DocRetry> docRetries = docRetryRepository.findByTimesFailedLessThanEqual(applicationProperties.getMaxDocRetries());
        if (! docRetries.isEmpty()) {
            retryFailedDocuments(docRetries);
        }
    }

    private void retryFailedDocuments(final List<DocRetry> docsToRetry) {
        final Set<String> shopIds = docsToRetry.stream().map(DocRetry::getShopId).collect(Collectors.toSet());
        final Set<String> docIds = docsToRetry.stream().map(DocRetry::getDocId).collect(Collectors.toSet());
        final List<MiraklShopDocument> shopDocuments = miraklMarketplacePlatformOperatorApiClient.getShopDocuments(new MiraklGetShopDocumentsRequest(shopIds));
        final List<MiraklShopDocument> filteredShopDocuments = shopDocuments.stream().filter(shopDocument -> docIds.contains(shopDocument.getId())).collect(Collectors.toList());
        processDocs(filteredShopDocuments);
    }

    private void updateDocument(final MiraklShopDocument document, DocumentDetail.DocumentTypeEnum type, String shareholderCode) {
        FileWrapper fileWrapper = downloadSelectedDocument(document);
        try {
            uploadDocumentToAdyen(type, fileWrapper, document.getShopId(), shareholderCode);
            docRetryRepository.findOneByDocId(document.getId()).ifPresent(docRetry -> {
                docErrorRepository.delete(docRetry.getDocErrors());
                docRetryRepository.delete(docRetry.getId());
            });
        } catch (ApiException e) {
            log.error("MarketPay Api Exception: {}, {}. For the Shop: {}", e.getError(), e, document.getShopId());
            storeDocumentForRetry(document.getId(), document.getShopId(), e.toString());
        } catch (Exception e) {
            log.error("Exception: {}, {}. For the Shop: {}", e.getMessage(), e, document.getShopId());
            storeDocumentForRetry(document.getId(), document.getShopId(), e.toString());
        }
    }

    private void storeDocumentForRetry(String documentId, String shopId, String error) {
        DocRetry docRetry = docRetryRepository.findOneByDocId(documentId).orElse(null);
        Integer timesFailed;
        if (docRetry != null) {
            timesFailed = docRetry.getTimesFailed() + 1;
        } else {
            timesFailed = 1;
            docRetry = new DocRetry();
        }
        final DocError docError = new DocError();
        docError.setError(error);
        docError.setDocRetry(docRetry);
        docRetry.setDocId(documentId);
        docRetry.addDocError(docError);
        docRetry.setShopId(shopId);
        docRetry.setTimesFailed(timesFailed);
        docRetryRepository.saveAndFlush(docRetry);
        docErrorRepository.saveAndFlush(docError);
    }

    private void updateDocument(final MiraklShopDocument document, DocumentDetail.DocumentTypeEnum type) {
        updateDocument(document, type, null);
    }

    /**
     * Retrieve documents from Mirakl(S30)
     */
    private List<MiraklShopDocument> retrieveUpdatedDocs() {
        //To replace with MiraklGetShopDocumentsRequest when fixed
        GetShopDocumentsRequest request = new GetShopDocumentsRequest();
        request.setUpdatedSince(deltaService.getDocumentDelta());
        log.debug("getShopDocuments request since: {}", request.getUpdatedSince());
        return miraklMarketplacePlatformOperatorApiClient.getShopDocuments(request);
    }

    /**
     * Download one document from Mirakl(S31), it will always be a single document, this prevents mirakl from returning a zip file, which is not supported on Adyen
     */
    private FileWrapper downloadSelectedDocument(MiraklShopDocument document) {
        MiraklDownloadShopsDocumentsRequest request = new MiraklDownloadShopsDocumentsRequest();
        List<String> documentIds = new ArrayList<>();
        documentIds.add(document.getId());
        request.setDocumentIds(documentIds);
        return miraklMarketplacePlatformOperatorApiClient.downloadShopsDocuments(request);
    }

    /**
     * Encode document retrieved from Mirakl in Base64 and push it to Adyen, if the document type is BANK_STATEMENT/adyen-bankproof, a bank account is needed
     */
    private void uploadDocumentToAdyen(DocumentDetail.DocumentTypeEnum documentType, FileWrapper fileWrapper, String shopId, String shareholderCode) throws Exception {
        UploadDocumentRequest request = new UploadDocumentRequest();

        DocumentDetail documentDetail = new DocumentDetail();
        documentDetail.setAccountHolderCode(shopId);
        documentDetail.setShareholderCode(shareholderCode);
        documentDetail.setDocumentType(documentType);
        documentDetail.setFilename(fileWrapper.getFilename());

        //Encode file Base64
        byte[] bytes = toByteArray(fileWrapper.getFile());
        Base64.Encoder encoder = Base64.getEncoder();
        String encoded = encoder.encodeToString(bytes);
        request.setDocumentContent(encoded);

        //If document is a bank statement, the bankaccountUUID is required
        if (documentType.equals(DocumentDetail.DocumentTypeEnum.BANK_STATEMENT)) {
            String UUID = retrieveBankAccountUUID(shopId);
            if (UUID != null && ! UUID.isEmpty()) {
                documentDetail.setBankAccountUUID(UUID);
            } else {
                throw new IllegalStateException("No bank accounts are associated with this shop, a bank account is needed to upload a bank statement");
            }
        }

        // For test add PASSED to get document in payout mode
        if(environment.equals(Environment.TEST.name())) {
            documentDetail.setDescription("PASSED");
        }

        request.setDocumentDetail(documentDetail);
        UploadDocumentResponse response = adyenAccountService.uploadDocument(request);
        log.debug("Account holder code: {}", shareholderCode);
        log.debug("Shop ID: {}", shopId);
        log.debug("DocumentType: {}", documentType);
        log.debug("UploadDocumentResponse: {}", response.toString());
    }

    /**
     * Call to Adyen to retrieve the (first)bankaccountUUID
     */
    private String retrieveBankAccountUUID(String shopID) throws Exception {
        GetAccountHolderRequest getAccountHolderRequest = new GetAccountHolderRequest();
        getAccountHolderRequest.setAccountHolderCode(shopID);
        GetAccountHolderResponse getAccountHolderResponse = adyenAccountService.getAccountHolder(getAccountHolderRequest);
        if (! getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().isEmpty()) {
            return getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().get(0).getBankAccountUUID();
        }
        return null;
    }

    public void removeMiraklMediaForShareHolder(final String shareHolderCode) {
        ShareholderMapping shareholderMapping = shareholderMappingRepository.findOneByAdyenShareholderCode(shareHolderCode)
                                                                            .orElseThrow(() -> new IllegalStateException("No shareholder mapping found for shareholder code: " + shareHolderCode));
        final List<MiraklShopDocument> shopDocuments = miraklMarketplacePlatformOperatorApiClient.getShopDocuments(new MiraklGetShopDocumentsRequest(ImmutableList.of(shareholderMapping.getMiraklShopId())));
        List<String> documentIdsToDelete = extractDocumentsToDelete(shopDocuments, UBO_ENTITY_TYPE, shareholderMapping.getMiraklUboNumber());

        documentIdsToDelete.forEach(docIdToDel -> {
            final MiraklDeleteShopDocumentRequest request = new MiraklDeleteShopDocumentRequest(docIdToDel);
            miraklMarketplacePlatformOperatorApiClient.deleteShopDocument(request);
        });
    }

    public void removeMiraklMediaForIndividual(final String shopId) {
        final List<MiraklShopDocument> shopDocuments = miraklMarketplacePlatformOperatorApiClient.getShopDocuments(new MiraklGetShopDocumentsRequest(ImmutableList.of(shopId)));
        List<String> documentIdsToDelete = extractDocumentsToDelete(shopDocuments, INDIVIDUAL_ENTITY_TYPE, null);

        documentIdsToDelete.forEach(docIdToDel -> {
            final MiraklDeleteShopDocumentRequest request = new MiraklDeleteShopDocumentRequest(docIdToDel);
            miraklMarketplacePlatformOperatorApiClient.deleteShopDocument(request);
        });
    }

    private List<String> extractDocumentsToDelete(final List<MiraklShopDocument> shopDocuments, String entityType, Integer uboNumber) {
        String uboStartingTypeCode = "adyen-" + entityType + Objects.toString(uboNumber, "");
        return shopDocuments.stream().filter(x -> x.getTypeCode().startsWith(uboStartingTypeCode)).map(MiraklShopDocument::getId).collect(Collectors.toList());
    }

    public void removeMiraklMediaForBankProof(final String accountHolderCode) {
        final List<MiraklShopDocument> shopDocuments = miraklMarketplacePlatformOperatorApiClient.getShopDocuments(new MiraklGetShopDocumentsRequest(ImmutableList.of(accountHolderCode)));
        List<String> documentIdsToDelete = extractBankProofDocumentsToDelete(shopDocuments);

        documentIdsToDelete.forEach(docIdToDel -> {
            final MiraklDeleteShopDocumentRequest request = new MiraklDeleteShopDocumentRequest(docIdToDel);
            miraklMarketplacePlatformOperatorApiClient.deleteShopDocument(request);
        });

    }

    private List<String> extractBankProofDocumentsToDelete(final List<MiraklShopDocument> shopDocuments) {
        return shopDocuments.stream().filter(x -> x.getTypeCode().contentEquals(Constants.BANKPROOF)).map(MiraklShopDocument::getId).collect(Collectors.toList());
    }
}
