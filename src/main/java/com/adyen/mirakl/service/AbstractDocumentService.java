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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Resource;
import org.apache.commons.lang3.EnumUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.adyen.mirakl.service.dto.DocumentDTO;
import com.adyen.model.marketpay.DocumentDetail;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.domain.shop.document.MiraklShopDocument;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;

@Service
public abstract class AbstractDocumentService<T extends DocumentDTO> {

    private final Logger log = LoggerFactory.getLogger(AbstractDocumentService.class);

    private static final String ADYEN_PREFIX = "adyen-";
    private static final String SUFFIX_MIRAKL_PHOTOID = "-photoid";
    private static final String SUFFIX_MIRAKL_PHOTOID_REAR = "-photoid-rear";
    private static final String SUFFIX_MIRAKL_PHOTOIDTYPE = "-photoidtype";
    private static final String SUFFIX_FRONT = "_FRONT";
    private static final String SUFFIX_BACK = "_BACK";

    @Resource
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient;

    public abstract List<T> extractDocuments(List<MiraklShopDocument> miraklShopDocuments);

    abstract void addDocumentDTO(final ImmutableList.Builder<T> builder,
                                 final MiraklShopDocument miraklShopDocument,
                                 final Integer entitySequence,
                                 final Map<Boolean, DocumentDetail.DocumentTypeEnum> documentTypeEnum);

    void addToBuilder(ImmutableList.Builder<T> builder, Map<String, String> internalMemoryForDocs, MiraklShopDocument miraklShopDocument, String entityType, Integer entitySequence) {
        String entityName = entityType + Objects.toString(entitySequence, "");
        String photoIdFront = ADYEN_PREFIX + entityName + SUFFIX_MIRAKL_PHOTOID;
        String photoIdRear = ADYEN_PREFIX + entityName + SUFFIX_MIRAKL_PHOTOID_REAR;

        if (miraklShopDocument.getTypeCode().equalsIgnoreCase(photoIdFront)) {
            final Map<Boolean, DocumentDetail.DocumentTypeEnum> documentTypeEnum = findCorrectEnum(internalMemoryForDocs, miraklShopDocument, entityName, SUFFIX_FRONT);
            if (documentTypeEnum != null) {
                addDocumentDTO(builder, miraklShopDocument, entitySequence, documentTypeEnum);
            } else {
                log.info("DocumentType is not supported for {}, shop: [{}], skipping uboDocument", entityName, miraklShopDocument.getShopId());
            }
        }

        if (miraklShopDocument.getTypeCode().equalsIgnoreCase(photoIdRear)) {
            final Map<Boolean, DocumentDetail.DocumentTypeEnum> documentTypeEnum = findCorrectEnum(internalMemoryForDocs, miraklShopDocument, entityName, SUFFIX_BACK);
            // If the enum + BACK_SUFFIX is not found as an enum then do not send it across
            if (documentTypeEnum != null && documentTypeEnum.keySet().iterator().next()) {
                addDocumentDTO(builder, miraklShopDocument, entitySequence, documentTypeEnum);
            } else if (documentTypeEnum != null) {
                log.info("DocumentType [{}] is not supported for {}, shop: [{}], skipping uboDocument",
                         documentTypeEnum.values().iterator().next() + SUFFIX_BACK,
                         entityName,
                         miraklShopDocument.getShopId());
            } else {
                log.warn("DocumentType is not supported for {}, shop: [{}], skipping uboDocument, please check your documentTypes in your customfields settings on Mirakl",
                         entityName,
                         miraklShopDocument.getShopId());
            }
        }
    }

    private Map<Boolean, DocumentDetail.DocumentTypeEnum> findCorrectEnum(final Map<String, String> internalMemoryForDocs,
                                                                          final MiraklShopDocument miraklShopDocument,
                                                                          final String entityName,
                                                                          String suffix) {
        String documentType = retrieveDocumentType(entityName, miraklShopDocument.getShopId(), internalMemoryForDocs);
        if (documentType != null) {
            if (EnumUtils.isValidEnum(DocumentDetail.DocumentTypeEnum.class, documentType + suffix)) {
                return ImmutableMap.of(true, DocumentDetail.DocumentTypeEnum.valueOf(documentType + suffix));
            } else {
                return ImmutableMap.of(false, DocumentDetail.DocumentTypeEnum.valueOf(documentType));
            }
        }
        return null;
    }

    private String retrieveDocumentType(final String entityName, final String shopId, Map<String, String> internalMemory) {
        String documentTypeEnum = internalMemory.getOrDefault(shopId + "_" + entityName, null);
        if (documentTypeEnum == null) {
            String docTypeFromMirakl = getDocTypeFromMirakl(entityName, shopId);
            if (docTypeFromMirakl != null) {
                internalMemory.put(shopId + "_" + entityName, docTypeFromMirakl);
                return docTypeFromMirakl;
            }
        }
        return documentTypeEnum;
    }

    private String getDocTypeFromMirakl(String entityName, String shopId) {
        MiraklGetShopsRequest request = new MiraklGetShopsRequest();
        request.setShopIds(ImmutableList.of(shopId));
        MiraklShops shops = miraklMarketplacePlatformOperatorApiClient.getShops(request);
        MiraklShop shop = shops.getShops().iterator().next();
        String code = ADYEN_PREFIX + entityName + SUFFIX_MIRAKL_PHOTOIDTYPE;
        Optional<MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue> photoIdType = shop.getAdditionalFieldValues()
                                                                                                   .stream()
                                                                                                   .filter(MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue.class::isInstance)
                                                                                                   .map(MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue.class::cast)
                                                                                                   .filter(x -> code.equalsIgnoreCase(x.getCode()))
                                                                                                   .findAny();
        return photoIdType.map(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue::getValue).orElse(null);
    }
}
