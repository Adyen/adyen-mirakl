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
 * Copyright (c) 2019 Adyen B.V.
 * This file is open source and available under the MIT license.
 * See the LICENSE file for more info.
 */

package com.adyen.mirakl.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.adyen.mirakl.domain.DocError;
import com.adyen.mirakl.domain.DocRetry;
import com.adyen.mirakl.domain.ShareholderMapping;
import com.adyen.mirakl.repository.DocErrorRepository;
import com.adyen.mirakl.repository.DocRetryRepository;
import com.adyen.mirakl.repository.ShareholderMappingRepository;
import com.adyen.mirakl.service.dto.UboDocumentDTO;
import com.adyen.model.marketpay.DocumentDetail;
import com.google.common.collect.ImmutableList;
import com.mirakl.client.mmp.domain.shop.document.MiraklShopDocument;

@Service
public class UboDocumentService extends AbstractDocumentService<UboDocumentDTO> {

    private final Logger log = LoggerFactory.getLogger(UboDocumentService.class);

    private static final String UBO_ENTITY = "ubo";

    @Resource
    private DocRetryRepository docRetryRepository;

    @Resource
    private DocErrorRepository docErrorRepository;

    @Resource
    private ShareholderMappingRepository shareholderMappingRepository;

    @Value("${shopService.maxUbos}")
    private Integer maxUbos = 4;

    @Override
    public List<UboDocumentDTO> extractDocuments(List<MiraklShopDocument> miraklShopDocuments) {
        ImmutableList.Builder<UboDocumentDTO> builder = ImmutableList.builder();

        Map<String, String> internalMemoryForDocs = new HashMap<>();
        miraklShopDocuments.forEach(miraklShopDocument -> {
            for (Integer uboNumber = 1; uboNumber <= maxUbos; uboNumber++) {
                addToBuilder(builder, internalMemoryForDocs, miraklShopDocument, UBO_ENTITY, uboNumber);
            }
        });

        return builder.build();
    }

    @Override
    void addDocumentDTO(final ImmutableList.Builder<UboDocumentDTO> builder,
                                final MiraklShopDocument miraklShopDocument,
                                final Integer entitySequence,
                                final Map<Boolean, DocumentDetail.DocumentTypeEnum> documentTypeEnum) {
        final Optional<ShareholderMapping> shareholderMapping = shareholderMappingRepository.findOneByMiraklShopIdAndMiraklUboNumber(miraklShopDocument.getShopId(), entitySequence);
        if (shareholderMapping.isPresent()) {
            final UboDocumentDTO uboDocumentDTO = new UboDocumentDTO();
            uboDocumentDTO.setDocumentTypeEnum(documentTypeEnum.values().iterator().next());
            uboDocumentDTO.setMiraklShopDocument(miraklShopDocument);
            uboDocumentDTO.setShareholderCode(shareholderMapping.get().getAdyenShareholderCode());
            builder.add(uboDocumentDTO);
        } else {
            log.warn("No shareholder mapping found for ubo: [{}], shop: [{}], storing uboDocument for retry", entitySequence, miraklShopDocument.getShopId());
            storeDocumentForRetry(miraklShopDocument.getId(), miraklShopDocument.getShopId());
        }

    }

    public void setMaxUbos(Integer maxUbos) {
        this.maxUbos = maxUbos;
    }

    private void storeDocumentForRetry(String documentId, String shopId) {
        DocRetry docRetry = docRetryRepository.findOneByDocId(documentId).orElse(null);
        Integer timesFailed;
        if (docRetry != null) {
            timesFailed = docRetry.getTimesFailed() + 1;
        } else {
            timesFailed = 1;
            docRetry = new DocRetry();
        }
        final DocError docError = new DocError();
        docError.setError("No shareholder mapping found for ubo");
        docError.setDocRetry(docRetry);
        docRetry.setDocId(documentId);
        docRetry.addDocError(docError);
        docRetry.setShopId(shopId);
        docRetry.setTimesFailed(timesFailed);
        docRetryRepository.saveAndFlush(docRetry);
        docErrorRepository.saveAndFlush(docError);
    }
}
