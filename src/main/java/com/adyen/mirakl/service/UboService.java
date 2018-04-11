package com.adyen.mirakl.service;

import com.adyen.mirakl.domain.ShareholderMapping;
import com.adyen.mirakl.domain.StreetDetails;
import com.adyen.mirakl.repository.ShareholderMappingRepository;
import com.adyen.mirakl.service.dto.UboDocumentDTO;
import com.adyen.mirakl.service.util.IsoUtil;
import com.adyen.model.Address;
import com.adyen.model.Name;
import com.adyen.model.marketpay.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.domain.shop.document.MiraklShopDocument;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;
import org.apache.commons.lang3.EnumUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class UboService {

    private static final Logger log = LoggerFactory.getLogger(UboService.class);

    private static final String ADYEN_UBO = "adyen-ubo";

    public static final String CIVILITY = "civility";
    public static final String FIRSTNAME = "firstname";
    public static final String LASTNAME = "lastname";
    public static final String EMAIL = "email";
    public static final String DATE_OF_BIRTH = "dob";
    public static final String NATIONALITY = "nationality";
    public static final String ID_NUMBER = "idnumber";
    public static final String HOUSE_NUMBER_OR_NAME = "housenumber";
    public static final String STREET = "streetname";
    public static final String CITY = "city";
    public static final String POSTAL_CODE = "zip";
    public static final String COUNTRY = "country";
    public static final String PHONE_COUNTRY_CODE = "phonecountry";
    public static final String PHONE_TYPE = "phonetype";
    public static final String PHONE_NUMBER = "phonenumber";

    public final static Map<String, Name.GenderEnum> CIVILITY_TO_GENDER = ImmutableMap.<String, Name.GenderEnum>builder().put("MR", Name.GenderEnum.MALE)
                                                                                                                         .put("MRS", Name.GenderEnum.FEMALE)
                                                                                                                         .put("MISS", Name.GenderEnum.FEMALE)
                                                                                                                         .build();

    @Value("${shopService.maxUbos}")
    private Integer maxUbos = 4;

    @Resource
    private ShareholderMappingRepository shareholderMappingRepository;

    @Resource
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient;

    @Resource
    private Map<String, Pattern> houseNumberPatterns;

    /**
     * Extract shareholder contact data in a adyen format from a mirakl shop
     *
     * @param shop mirakl shop
     * @return share holder contacts to send to adyen
     */
    public List<ShareholderContact> extractUbos(final MiraklShop shop, final GetAccountHolderResponse existingAccountHolder) {
        Map<String, String> extractedKeysFromMirakl = extractKeysFromMirakl(shop);

        ImmutableList.Builder<ShareholderContact> builder = ImmutableList.builder();
        generateMiraklUboKeys(maxUbos).forEach((uboNumber, uboKeys) -> {
            String civility = extractedKeysFromMirakl.getOrDefault(uboKeys.get(CIVILITY), null);
            String firstName = extractedKeysFromMirakl.getOrDefault(uboKeys.get(FIRSTNAME), null);
            String lastName = extractedKeysFromMirakl.getOrDefault(uboKeys.get(LASTNAME), null);
            String email = extractedKeysFromMirakl.getOrDefault(uboKeys.get(EMAIL), null);
            String dateOfBirth = extractedKeysFromMirakl.getOrDefault(uboKeys.get(DATE_OF_BIRTH), null);
            String nationality = extractedKeysFromMirakl.getOrDefault(uboKeys.get(NATIONALITY), null);
            String idNumber = extractedKeysFromMirakl.getOrDefault(uboKeys.get(ID_NUMBER), null);
            String houseNumberOrName = extractedKeysFromMirakl.getOrDefault(uboKeys.get(HOUSE_NUMBER_OR_NAME), null);
            String street = extractedKeysFromMirakl.getOrDefault(uboKeys.get(STREET), null);
            String city = extractedKeysFromMirakl.getOrDefault(uboKeys.get(CITY), null);
            String postalCode = extractedKeysFromMirakl.getOrDefault(uboKeys.get(POSTAL_CODE), null);
            String country = extractedKeysFromMirakl.getOrDefault(uboKeys.get(COUNTRY), null);
            String phoneCountryCode = extractedKeysFromMirakl.getOrDefault(uboKeys.get(PHONE_COUNTRY_CODE), null);
            String phoneType = extractedKeysFromMirakl.getOrDefault(uboKeys.get(PHONE_TYPE), null);
            String phoneNumber = extractedKeysFromMirakl.getOrDefault(uboKeys.get(PHONE_NUMBER), null);

            //do nothing if mandatory fields are missing
            if (allMandatoryDataIsAvailable(civility, firstName, lastName, email)) {
                ShareholderContact shareholderContact = new ShareholderContact();
                addShareholderCode(shop, uboNumber, shareholderContact, existingAccountHolder);
                addMandatoryData(civility, firstName, lastName, email, shareholderContact);
                addPersonalData(uboNumber, dateOfBirth, nationality, idNumber, shareholderContact);
                String shopCountry = shop.getContactInformation().getCountry();
                addAddressData(uboNumber, houseNumberOrName, street, city, postalCode, country, shareholderContact, shopCountry);
                addPhoneData(uboNumber, phoneCountryCode, phoneType, phoneNumber, shareholderContact);
                builder.add(shareholderContact);
            }
        });
        return builder.build();
    }

    private boolean allMandatoryDataIsAvailable(final String civility, final String firstName, final String lastName, final String email) {
        return firstName != null && lastName != null && civility != null && email != null;
    }

    private Map<String, String> extractKeysFromMirakl(final MiraklShop shop) {
        return shop.getAdditionalFieldValues()
            .stream()
            .filter(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue.class::isInstance)
            .map(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue.class::cast)
            .collect(Collectors.toMap(MiraklAdditionalFieldValue::getCode,
                MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue::getValue));
    }


    public Set<Integer> extractUboNumbersFromShop(final MiraklShop miraklShop) {
        Map<String, String> extractedKeysFromMirakl = extractKeysFromMirakl(miraklShop);
        final ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
        generateMiraklUboKeys(maxUbos).forEach((uboNumber, uboKeys) -> {
            String civility = extractedKeysFromMirakl.getOrDefault(uboKeys.get(CIVILITY), null);
            String firstName = extractedKeysFromMirakl.getOrDefault(uboKeys.get(FIRSTNAME), null);
            String lastName = extractedKeysFromMirakl.getOrDefault(uboKeys.get(LASTNAME), null);
            String email = extractedKeysFromMirakl.getOrDefault(uboKeys.get(EMAIL), null);
            if(allMandatoryDataIsAvailable(civility, firstName, lastName, email)){
                builder.add(uboNumber);
            }
        });
        return builder.build();
    }

    public List<UboDocumentDTO> extractUboDocuments(List<MiraklShopDocument> miraklUbos) {

        ImmutableList.Builder<UboDocumentDTO> builder = ImmutableList.builder();

        Map<String, String> internalMemoryForDocs = new HashMap<>();
        miraklUbos.forEach(miraklShopDocument -> {
            for (int uboNumber = 1; uboNumber <= maxUbos; uboNumber++) {
                addToBuilder(builder, internalMemoryForDocs, miraklShopDocument, uboNumber);
            }
        });

        return builder.build();
    }

    private void addToBuilder(ImmutableList.Builder<UboDocumentDTO> builder, Map<String, String> internalMemoryForDocs, MiraklShopDocument miraklShopDocument, int uboNumber) {
        String photoIdFront = ADYEN_UBO + uboNumber + "-photoid";
        String photoIdRear = ADYEN_UBO + uboNumber + "-photoid-rear";
        if (miraklShopDocument.getTypeCode().equalsIgnoreCase(photoIdFront)) {
            final Map<Boolean, DocumentDetail.DocumentTypeEnum> documentTypeEnum = findCorrectEnum(internalMemoryForDocs, miraklShopDocument, uboNumber, "_FRONT");
            if (documentTypeEnum != null) {
                addUboDocumentDTO(builder, miraklShopDocument, uboNumber, documentTypeEnum);
            }
        }
        if (miraklShopDocument.getTypeCode().equalsIgnoreCase(photoIdRear)) {
            final Map<Boolean, DocumentDetail.DocumentTypeEnum> documentTypeEnum = findCorrectEnum(internalMemoryForDocs, miraklShopDocument, uboNumber, "_BACK");
            //ignore if the result is could not convert to enum with suffix
            if (documentTypeEnum != null && documentTypeEnum.keySet().iterator().next()) {
                addUboDocumentDTO(builder, miraklShopDocument, uboNumber, documentTypeEnum);
            }
        }
    }

    private void addUboDocumentDTO(final ImmutableList.Builder<UboDocumentDTO> builder,
                                   final MiraklShopDocument miraklShopDocument,
                                   final int uboNumber,
                                   final Map<Boolean, DocumentDetail.DocumentTypeEnum> documentTypeEnum) {

        final Optional<ShareholderMapping> shareholderMapping = shareholderMappingRepository.findOneByMiraklShopIdAndMiraklUboNumber(miraklShopDocument.getShopId(), uboNumber);
        if(shareholderMapping.isPresent()){
            final UboDocumentDTO uboDocumentDTO = new UboDocumentDTO();
            uboDocumentDTO.setDocumentTypeEnum(documentTypeEnum.values().iterator().next());
            uboDocumentDTO.setMiraklShopDocument(miraklShopDocument);
            uboDocumentDTO.setShareholderCode(shareholderMapping.get().getAdyenShareholderCode());
            builder.add(uboDocumentDTO);
        }else{
            log.warn("No shareholder mapping found for ubo: [{}], shop: [{}], skipping uboDocument", uboNumber, miraklShopDocument.getShopId());
        }

    }

    private Map<Boolean, DocumentDetail.DocumentTypeEnum> findCorrectEnum(final Map<String, String> internalMemoryForDocs,
                                                                          final MiraklShopDocument miraklShopDocument,
                                                                          final int uboNumber,
                                                                          String suffix) {
        String documentType = retrieveUboPhotoIdType(uboNumber, miraklShopDocument.getShopId(), internalMemoryForDocs);
        if (documentType != null) {
            if (EnumUtils.isValidEnum(DocumentDetail.DocumentTypeEnum.class, documentType + suffix)) {
                return ImmutableMap.of(true, DocumentDetail.DocumentTypeEnum.valueOf(documentType + suffix));
            } else {
                return ImmutableMap.of(false, DocumentDetail.DocumentTypeEnum.valueOf(documentType));
            }
        }
        return null;
    }

    private String retrieveUboPhotoIdType(final Integer uboNumber, final String shopId, Map<String, String> internalMemory) {
        String documentTypeEnum = internalMemory.getOrDefault(shopId + "_" + uboNumber, null);
        if (documentTypeEnum == null) {
            String docTypeFromMirakl = getDocTypeFromMirakl(uboNumber, shopId);
            if (docTypeFromMirakl != null) {
                internalMemory.put(shopId + "_" + uboNumber, docTypeFromMirakl);
                return docTypeFromMirakl;
            }
        }
        return documentTypeEnum;
    }

    private String getDocTypeFromMirakl(Integer uboNumber, String shopId) {
        MiraklGetShopsRequest request = new MiraklGetShopsRequest();
        request.setShopIds(ImmutableList.of(shopId));
        MiraklShops shops = miraklMarketplacePlatformOperatorApiClient.getShops(request);
        MiraklShop shop = shops.getShops().iterator().next();
        String code = ADYEN_UBO + uboNumber + "-photoidtype";
        Optional<MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue> photoIdType = shop.getAdditionalFieldValues()
                                                                                                   .stream()
                                                                                                   .filter(MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue.class::isInstance)
                                                                                                   .map(MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue.class::cast)
                                                                                                   .filter(x -> code.equalsIgnoreCase(x.getCode()))
                                                                                                   .findAny();
        return photoIdType.map(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue::getValue).orElse(null);
    }

    public List<ShareholderContact> extractUbos(final MiraklShop shop) {
        return extractUbos(shop, null);
    }

    private void addShareholderCode(final MiraklShop shop, final Integer uboNumber, final ShareholderContact shareholderContact, final GetAccountHolderResponse existingAccountHolder) {
        final Optional<ShareholderMapping> mapping = shareholderMappingRepository.findOneByMiraklShopIdAndMiraklUboNumber(shop.getId(), uboNumber);
        mapping.ifPresent(shareholderMapping -> shareholderContact.setShareholderCode(shareholderMapping.getAdyenShareholderCode()));
        if (!mapping.isPresent()
            && existingAccountHolder != null
            && existingAccountHolder.getAccountHolderDetails() != null
            && existingAccountHolder.getAccountHolderDetails().getBusinessDetails() != null
            && ! CollectionUtils.isEmpty(existingAccountHolder.getAccountHolderDetails().getBusinessDetails().getShareholders())) {
            final List<ShareholderContact> shareholders = existingAccountHolder.getAccountHolderDetails().getBusinessDetails().getShareholders();
            if (uboNumber - 1 < shareholders.size()) {
                final String shareholderCode = shareholders.get(uboNumber - 1).getShareholderCode();
                if(mappingDoesNotAlreadyExist(shareholderCode)){
                    final ShareholderMapping shareholderMapping = new ShareholderMapping();
                    shareholderMapping.setAdyenShareholderCode(shareholderCode);
                    shareholderMapping.setMiraklShopId(shop.getId());
                    shareholderMapping.setMiraklUboNumber(uboNumber);
                    shareholderMappingRepository.saveAndFlush(shareholderMapping);
                    shareholderContact.setShareholderCode(shareholderCode);
                }
            }
        }
    }

    private boolean mappingDoesNotAlreadyExist(final String shareholderCode) {
        return !shareholderMappingRepository.findOneByAdyenShareholderCode(shareholderCode).isPresent();
    }

    private void addMandatoryData(final String civility, final String firstName, final String lastName, final String email, final ShareholderContact shareholderContact) {
        Name name = new Name();
        name.setGender(CIVILITY_TO_GENDER.getOrDefault(civility.toUpperCase(), Name.GenderEnum.UNKNOWN));
        name.setFirstName(firstName);
        name.setLastName(lastName);
        shareholderContact.setName(name);
        shareholderContact.setEmail(email);
    }

    private void addPhoneData(final Integer uboNumber, final String phoneCountryCode, final String phoneType, final String phoneNumber, final ShareholderContact shareholderContact) {
        if (phoneNumber != null || phoneType != null || phoneCountryCode != null) {
            final PhoneNumber phoneNumberWrapper = new PhoneNumber();
            Optional.ofNullable(phoneCountryCode).ifPresent(phoneNumberWrapper::setPhoneCountryCode);
            Optional.ofNullable(phoneNumber).ifPresent(phoneNumberWrapper::setPhoneNumber);
            Optional.ofNullable(phoneType).ifPresent(x -> phoneNumberWrapper.setPhoneType(PhoneNumber.PhoneTypeEnum.valueOf(x.toUpperCase())));
            shareholderContact.setPhoneNumber(phoneNumberWrapper);
        } else {
            log.warn("Unable to populate any phone data for share holder {}", uboNumber);
        }
    }

    private void addAddressData(final Integer uboNumber,
                                final String houseNumberOrName,
                                final String street,
                                final String city,
                                final String postalCode,
                                final String country,
                                final ShareholderContact shareholderContact,
                                final String contactCountry) {
        if (country != null || street != null || houseNumberOrName != null || city != null || postalCode != null) {
            final Address address = new Address();

            StreetDetails streetDetails = StreetDetails.createStreetDetailsFromSingleLine(houseNumberOrName, street, houseNumberPatterns.get(IsoUtil.getIso2CountryCodeFromIso3(contactCountry)));

            address.setStreet(streetDetails.getStreetName());
            address.setHouseNumberOrName(streetDetails.getHouseNumberOrName());

            Optional.ofNullable(city).ifPresent(address::setCity);
            Optional.ofNullable(postalCode).ifPresent(address::setPostalCode);
            Optional.ofNullable(country).ifPresent(address::setCountry);
            shareholderContact.setAddress(address);
        } else {
            log.warn("Unable to populate any address data for share holder {}", uboNumber);
        }
    }

    private void addPersonalData(final Integer uboNumber, final String dateOfBirth, final String nationality, final String idNumber, final ShareholderContact shareholderContact) {
        if (dateOfBirth != null || nationality != null || idNumber != null) {
            final PersonalData personalData = new PersonalData();
            Optional.ofNullable(dateOfBirth).ifPresent(personalData::setDateOfBirth);
            Optional.ofNullable(nationality).ifPresent(personalData::setNationality);
            Optional.ofNullable(idNumber).ifPresent(personalData::setIdNumber);
            shareholderContact.setPersonalData(personalData);
        } else {
            log.warn("Unable to populate any personal data for share holder {}", uboNumber);
        }
    }

    /**
     * generate mirakl ubo keys
     *
     * @param maxUbos number of ubos in mirakl e.g. 4
     * @return returns ubo numbers linked to their keys
     */
    public Map<Integer, Map<String, String>> generateMiraklUboKeys(Integer maxUbos) {
        return IntStream.rangeClosed(1, maxUbos).mapToObj(i -> {
            final Map<Integer, Map<String, String>> grouped = new HashMap<>();
            grouped.put(i,
                        new ImmutableMap.Builder<String, String>().put(CIVILITY, ADYEN_UBO + String.valueOf(i) + "-civility")
                                                                  .put(FIRSTNAME, ADYEN_UBO + String.valueOf(i) + "-firstname")
                                                                  .put(LASTNAME, ADYEN_UBO + String.valueOf(i) + "-lastname")
                                                                  .put(EMAIL, ADYEN_UBO + String.valueOf(i) + "-email")
                                                                  .put(DATE_OF_BIRTH, ADYEN_UBO + String.valueOf(i) + "-dob")
                                                                  .put(NATIONALITY, ADYEN_UBO + String.valueOf(i) + "-nationality")
                                                                  .put(ID_NUMBER, ADYEN_UBO + String.valueOf(i) + "-idnumber")
                                                                  .put(HOUSE_NUMBER_OR_NAME, ADYEN_UBO + String.valueOf(i) + "-housenumber")
                                                                  .put(STREET, ADYEN_UBO + String.valueOf(i) + "-streetname")
                                                                  .put(CITY, ADYEN_UBO + String.valueOf(i) + "-city")
                                                                  .put(POSTAL_CODE, ADYEN_UBO + String.valueOf(i) + "-zip")
                                                                  .put(COUNTRY, ADYEN_UBO + String.valueOf(i) + "-country")
                                                                  .put(PHONE_COUNTRY_CODE, ADYEN_UBO + String.valueOf(i) + "-phonecountry")
                                                                  .put(PHONE_TYPE, ADYEN_UBO + String.valueOf(i) + "-phonetype")
                                                                  .put(PHONE_NUMBER, ADYEN_UBO + String.valueOf(i) + "-phonenumber")
                                                                  .build());
            return grouped;
        }).reduce((x, y) -> {
            x.put(y.entrySet().iterator().next().getKey(), y.entrySet().iterator().next().getValue());
            return x;
        }).orElseThrow(() -> new IllegalStateException("UBOs must exist, number found: " + maxUbos));
    }

    public void setMaxUbos(final Integer maxUbos) {
        this.maxUbos = maxUbos;
    }

    public void setHouseNumberPatterns(final Map<String, Pattern> houseNumberPatterns) {
        this.houseNumberPatterns = houseNumberPatterns;
    }
}
