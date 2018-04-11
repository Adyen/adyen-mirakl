package com.adyen.mirakl.service;

import com.adyen.mirakl.config.Constants;
import com.adyen.mirakl.domain.DocError;
import com.adyen.mirakl.domain.DocRetry;
import com.adyen.mirakl.domain.ShareholderMapping;
import com.adyen.mirakl.repository.DocErrorRepository;
import com.adyen.mirakl.repository.DocRetryRepository;
import com.adyen.mirakl.repository.ShareholderMappingRepository;
import com.adyen.mirakl.service.dto.UboDocumentDTO;
import com.adyen.mirakl.service.util.GetShopDocumentsRequest;
import com.adyen.model.marketpay.*;
import com.adyen.service.Account;
import com.adyen.service.exception.ApiException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mirakl.client.mmp.domain.common.FileWrapper;
import com.mirakl.client.mmp.domain.shop.document.MiraklShopDocument;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.request.shop.document.MiraklDeleteShopDocumentRequest;
import com.mirakl.client.mmp.request.shop.document.MiraklDownloadShopsDocumentsRequest;
import com.mirakl.client.mmp.request.shop.document.MiraklGetShopDocumentsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.io.Files.toByteArray;

@Service
@Transactional
public class DocService {

    private final Logger log = LoggerFactory.getLogger(DocService.class);

    @Resource
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient;

    @Resource
    private Account adyenAccountService;

    @Resource
    private DeltaService deltaService;

    @Resource
    private UboService uboService;

    @Resource
    private ShareholderMappingRepository shareholderMappingRepository;

    @Resource
    private DocRetryRepository docRetryRepository;

    @Resource
    private DocErrorRepository docErrorRepository;

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
        final ImmutableSet.Builder<MiraklShopDocument> unprocessed = ImmutableSet.builder();
        for (MiraklShopDocument document : miraklShopDocumentList) {
            if (Constants.BANKPROOF.equals(document.getTypeCode())) {
                updateDocument(document, DocumentDetail.DocumentTypeEnum.BANK_STATEMENT);
            }else{
                unprocessed.add(document);
            }
        }
        final List<UboDocumentDTO> uboDocumentDTOS = uboService.extractUboDocuments(miraklShopDocumentList);
        markMissingDocsAsFailed(uboDocumentDTOS.stream().map(UboDocumentDTO::getMiraklShopDocument).collect(Collectors.toSet()), unprocessed.build());
        uboDocumentDTOS.forEach(uboDocumentDTO -> updateDocument(uboDocumentDTO.getMiraklShopDocument(), uboDocumentDTO.getDocumentTypeEnum(), uboDocumentDTO.getShareholderCode()));
    }

    private void markMissingDocsAsFailed(final Set<MiraklShopDocument> uboDocsFound, final Set<MiraklShopDocument> unProcessedMiraklShopDocs) {
        final ImmutableSet<MiraklShopDocument> miraklShopDocumentsFailed = Sets.difference(unProcessedMiraklShopDocs, uboDocsFound).immutableCopy();
        miraklShopDocumentsFailed.forEach(failed -> storeDocumentForRetry(failed.getId(), failed.getShopId(), "Unable to extract UBO document DTO, check shareholder mapping exists"));
    }

    @Async
    public void retryDocumentsForShop(String shopId){
        final List<DocRetry> retryDocsByShopId = docRetryRepository.findByShopId(shopId);
        if(retryDocsByShopId.size() > 0){
            retryFailedDocuments(retryDocsByShopId);
        }
    }

    @Async
    public void retryFailedDocuments(){
        final List<DocRetry> docRetries = docRetryRepository.findAll();
        if(docRetries.size()>0){
            retryFailedDocuments(docRetries);
        }
    }

    private void retryFailedDocuments(final List<DocRetry> docsToRetry){
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

    private void storeDocumentForRetry(String documentId, String shopId, String error){
        DocRetry docRetry = docRetryRepository.findOneByDocId(documentId).orElse(null);
        Integer timesFailed;
        if(docRetry != null){
            timesFailed = docRetry.getTimesFailed() + 1;
        }else{
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
        request.setAccountHolderCode(shopId);
        request.setShareholderCode(shareholderCode);

        //Encode file Base64
        byte[] bytes = toByteArray(fileWrapper.getFile());
        Base64.Encoder encoder = Base64.getEncoder();
        String encoded = encoder.encodeToString(bytes);
        request.setDocumentContent(encoded);
        //If document is a bank statement, the bankaccountUUID is required
        if (documentType.equals(DocumentDetail.DocumentTypeEnum.BANK_STATEMENT)) {
            String UUID = retrieveBankAccountUUID(shopId);
            if (UUID != null && ! UUID.isEmpty()) {
                request.setBankAccountUUID(UUID);
            } else {
                throw new IllegalStateException("No bank accounts are associated with this shop, a bank account is needed to upload a bank statement");
            }
        }
        DocumentDetail documentDetail = new DocumentDetail();
        documentDetail.setFilename(fileWrapper.getFilename());
        documentDetail.setDocumentType(documentType);
        documentDetail.setShareholderCode(shareholderCode);
        documentDetail.setAccountHolderCode(shopId);
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
        ShareholderMapping shareholderMapping = shareholderMappingRepository.findOneByAdyenShareholderCode(shareHolderCode).orElseThrow(() -> new IllegalStateException("No shareholder mapping found for shareholder code: "+shareHolderCode));
        final List<MiraklShopDocument> shopDocuments = miraklMarketplacePlatformOperatorApiClient.getShopDocuments(new MiraklGetShopDocumentsRequest(ImmutableList.of(shareholderMapping.getMiraklShopId())));
        List<String> documentIdsToDelete = extractDocumentsToDelete(shopDocuments, shareholderMapping.getMiraklUboNumber());

        documentIdsToDelete.forEach(docIdToDel -> {
            final MiraklDeleteShopDocumentRequest request = new MiraklDeleteShopDocumentRequest(docIdToDel);
            miraklMarketplacePlatformOperatorApiClient.deleteShopDocument(request);
        });
    }

    private List<String> extractDocumentsToDelete(final List<MiraklShopDocument> shopDocuments, Integer uboNumber) {
        String uboStartingTypeCode = "adyen-ubo"+uboNumber;
        return shopDocuments.stream()
            .filter(x -> x.getTypeCode().startsWith(uboStartingTypeCode))
            .map(MiraklShopDocument::getId)
            .collect(Collectors.toList());
    }
}
