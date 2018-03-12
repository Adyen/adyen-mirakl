package com.adyen.mirakl.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.adyen.mirakl.service.util.ShopUtil;
import com.adyen.mirakl.startup.MiraklStartupValidator;
import com.adyen.model.Address;
import com.adyen.model.Name;
import com.adyen.model.marketpay.AccountHolderDetails;
import com.adyen.model.marketpay.BankAccountDetail;
import com.adyen.model.marketpay.BusinessDetails;
import com.adyen.model.marketpay.CreateAccountHolderRequest;
import com.adyen.model.marketpay.CreateAccountHolderRequest.LegalEntityEnum;
import com.adyen.model.marketpay.CreateAccountHolderResponse;
import com.adyen.model.marketpay.DeleteBankAccountRequest;
import com.adyen.model.marketpay.DeleteBankAccountResponse;
import com.adyen.model.marketpay.DocumentDetail;
import com.adyen.model.marketpay.GetAccountHolderRequest;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.IndividualDetails;
import com.adyen.model.marketpay.UpdateAccountHolderRequest;
import com.adyen.model.marketpay.UpdateAccountHolderResponse;
import com.adyen.model.marketpay.UploadDocumentRequest;
import com.adyen.model.marketpay.UploadDocumentResponse;
import com.adyen.service.Account;
import com.adyen.service.exception.ApiException;
import com.mirakl.client.mmp.domain.additionalfield.MiraklAdditionalFieldType;
import com.mirakl.client.mmp.domain.common.FileWrapper;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue;
import com.mirakl.client.mmp.domain.shop.MiraklContactInformation;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import com.mirakl.client.mmp.domain.shop.document.MiraklShopDocument;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;
import com.mirakl.client.mmp.request.shop.document.MiraklDownloadShopsDocumentsRequest;
import com.mirakl.client.mmp.request.shop.document.MiraklGetShopDocumentsRequest;
import static com.google.common.io.Files.toByteArray;

@Service
@Transactional
public class ShopService {

    private final Logger log = LoggerFactory.getLogger(ShopService.class);

    @Resource
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient;

    @Resource
    private Account adyenAccountService;

    @Resource
    private DeltaService deltaService;

    @Value("${shopService.maxUbos}")
    private Integer maxUbos = 4;

    public void retrieveUpdatedShops() {
        final ZonedDateTime beforeProcessing = ZonedDateTime.now();

        List<MiraklShop> shops = getUpdatedShops();
        log.debug("Retrieved shops: {}", shops.size());
        for (MiraklShop shop : shops) {
            try {
                GetAccountHolderResponse getAccountHolderResponse = getAccountHolderFromShop(shop);
                if (getAccountHolderResponse != null) {
                    processUpdateAccountHolder(shop, getAccountHolderResponse);
                } else {
                    processCreateAccountHolder(shop);
                }
            } catch (ApiException e) {
                log.error("MarketPay Api Exception: {}", e.getError(), e);
            } catch (Exception e) {
                log.error("Exception: {}", e.getMessage(), e);
            }
        }

        deltaService.createNewShopDelta(beforeProcessing);
    }

    private void processCreateAccountHolder(final MiraklShop shop) throws Exception {
        CreateAccountHolderRequest createAccountHolderRequest = createAccountHolderRequestFromShop(shop);
        CreateAccountHolderResponse response = adyenAccountService.createAccountHolder(createAccountHolderRequest);
        log.debug("CreateAccountHolderResponse: {}", response);
    }

    private void processUpdateAccountHolder(final MiraklShop shop, final GetAccountHolderResponse getAccountHolderResponse) throws Exception {
        Optional<UpdateAccountHolderRequest> updateAccountHolderRequest = updateAccountHolderRequestFromShop(shop, getAccountHolderResponse);
        if (updateAccountHolderRequest.isPresent()) {
            UpdateAccountHolderResponse response = adyenAccountService.updateAccountHolder(updateAccountHolderRequest.get());
            log.debug("UpdateAccountHolderResponse: {}", response);

            // if IBAN has changed remove the old one
            if (isIbanChanged(getAccountHolderResponse, shop)) {
                DeleteBankAccountResponse deleteBankAccountResponse = adyenAccountService.deleteBankAccount(deleteBankAccountRequest(getAccountHolderResponse));
                log.debug("DeleteBankAccountResponse: {}", deleteBankAccountResponse);
            }
        }
    }

    /**
     * Construct DeleteBankAccountRequest to remove outdated iban bankaccounts
     */
    protected DeleteBankAccountRequest deleteBankAccountRequest(GetAccountHolderResponse getAccountHolderResponse) {
        DeleteBankAccountRequest deleteBankAccountRequest = new DeleteBankAccountRequest();
        deleteBankAccountRequest.accountHolderCode(getAccountHolderResponse.getAccountHolderCode());
        List<String> uuids = new ArrayList<>();
        for (BankAccountDetail bankAccountDetail : getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails()) {
            uuids.add(bankAccountDetail.getBankAccountUUID());
        }
        deleteBankAccountRequest.setBankAccountUUIDs(uuids);

        return deleteBankAccountRequest;
    }

    public List<MiraklShop> getUpdatedShops() {
        int offset = 0;
        Long totalCount = 1L;
        List<MiraklShop> shops = new ArrayList<>();

        while (offset < totalCount) {
            MiraklGetShopsRequest miraklGetShopsRequest = new MiraklGetShopsRequest();
            miraklGetShopsRequest.setOffset(offset);

            miraklGetShopsRequest.setUpdatedSince(deltaService.getShopDelta());
            MiraklShops miraklShops = miraklMarketplacePlatformOperatorApiClient.getShops(miraklGetShopsRequest);
            shops.addAll(miraklShops.getShops());

            totalCount = miraklShops.getTotalCount();
            offset += miraklShops.getShops().size();
        }

        return shops;
    }

    private CreateAccountHolderRequest createAccountHolderRequestFromShop(MiraklShop shop) {
        CreateAccountHolderRequest createAccountHolderRequest = new CreateAccountHolderRequest();

        // Set Account holder code
        createAccountHolderRequest.setAccountHolderCode(shop.getId());

        // Set LegalEntity
        LegalEntityEnum legalEntity = getLegalEntityFromShop(shop);
        createAccountHolderRequest.setLegalEntity(legalEntity);

        // Set AccountHolderDetails
        AccountHolderDetails accountHolderDetails = new AccountHolderDetails();
        accountHolderDetails.setAddress(setAddressDetails(shop));
        accountHolderDetails.setBankAccountDetails(setBankAccountDetails(shop));

        if (LegalEntityEnum.INDIVIDUAL.equals(legalEntity)) {
            IndividualDetails individualDetails = createIndividualDetailsFromShop(shop);
            accountHolderDetails.setIndividualDetails(individualDetails);
        } else if (LegalEntityEnum.BUSINESS.equals(legalEntity)) {
            BusinessDetails businessDetails = createBusinessDetailsFromShop(shop);
            accountHolderDetails.setBusinessDetails(businessDetails);
        } else {
            throw new IllegalArgumentException(legalEntity.toString() + " not supported");
        }

        // Set email
        MiraklContactInformation contactInformation = getContactInformationFromShop(shop);
        accountHolderDetails.setEmail(contactInformation.getEmail());
        createAccountHolderRequest.setAccountHolderDetails(accountHolderDetails);

        return createAccountHolderRequest;
    }

    private LegalEntityEnum getLegalEntityFromShop(MiraklShop shop) {
        MiraklValueListAdditionalFieldValue additionalFieldValue = (MiraklValueListAdditionalFieldValue) shop.getAdditionalFieldValues()
                                                                                                             .stream()
                                                                                                             .filter(field -> isListWithCode(field,
                                                                                                                                             MiraklStartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE))
                                                                                                             .findAny()
                                                                                                             .orElseThrow(() -> new RuntimeException("Legal entity not found"));

        LegalEntityEnum legalEntity = Arrays.stream(LegalEntityEnum.values())
                                            .filter(legalEntityEnum -> legalEntityEnum.toString().equalsIgnoreCase(additionalFieldValue.getValue()))
                                            .findAny()
                                            .orElseThrow(() -> new RuntimeException("Invalid legal entity: " + additionalFieldValue.toString()));

        return legalEntity;
    }

    private MiraklContactInformation getContactInformationFromShop(MiraklShop shop) {
        return Optional.of(shop.getContactInformation()).orElseThrow(() -> new RuntimeException("Contact information not found"));
    }

    private Address setAddressDetails(MiraklShop shop) {
        MiraklContactInformation contactInformation = getContactInformationFromShop(shop);
        if (! contactInformation.getCountry().isEmpty()) {
            Address address = new Address();
            address.setHouseNumberOrName(getHouseNumberFromStreet(contactInformation.getStreet1()));
            address.setPostalCode(contactInformation.getZipCode());
            address.setStateOrProvince(contactInformation.getState());
            address.setStreet(contactInformation.getStreet1());
            address.setCountry(getIso2CountryCodeFromIso3(contactInformation.getCountry()));
            address.setCity(contactInformation.getCity());
            return address;
        }
        return null;
    }

    private BusinessDetails createBusinessDetailsFromShop(final MiraklShop shop) {
        BusinessDetails businessDetails = new BusinessDetails();

        if (shop.getProfessionalInformation() != null) {
            if (StringUtils.isNotEmpty(shop.getProfessionalInformation().getCorporateName())) {
                businessDetails.setLegalBusinessName(shop.getProfessionalInformation().getCorporateName());
            }
            if (StringUtils.isNotEmpty(shop.getProfessionalInformation().getTaxIdentificationNumber())) {
                businessDetails.setTaxId(shop.getProfessionalInformation().getTaxIdentificationNumber());
            }
        }

        businessDetails.setShareholders(ShopUtil.extractUbos(shop, maxUbos));
        return businessDetails;
    }

    private IndividualDetails createIndividualDetailsFromShop(MiraklShop shop) {
        IndividualDetails individualDetails = new IndividualDetails();

        MiraklContactInformation contactInformation = getContactInformationFromShop(shop);

        Name name = new Name();
        name.setFirstName(contactInformation.getFirstname());
        name.setLastName(contactInformation.getLastname());
        name.setGender(ShopUtil.CIVILITY_TO_GENDER.getOrDefault(contactInformation.getCivility(), Name.GenderEnum.UNKNOWN));
        individualDetails.setName(name);
        return individualDetails;
    }

    private boolean isListWithCode(MiraklAdditionalFieldValue additionalFieldValue, MiraklStartupValidator.CustomMiraklFields field) {
        return MiraklAdditionalFieldType.LIST.equals(additionalFieldValue.getFieldType()) && field.toString().equalsIgnoreCase(additionalFieldValue.getCode());
    }

    /**
     * Check if AccountHolder already exists in Adyen
     */
    private GetAccountHolderResponse getAccountHolderFromShop(MiraklShop shop) throws Exception {

        // lookup accountHolder in Adyen
        GetAccountHolderRequest getAccountHolderRequest = new GetAccountHolderRequest();
        getAccountHolderRequest.setAccountHolderCode(shop.getId());

        try {
            GetAccountHolderResponse getAccountHolderResponse = adyenAccountService.getAccountHolder(getAccountHolderRequest);
            if (! getAccountHolderResponse.getAccountHolderCode().isEmpty()) {
                return getAccountHolderResponse;
            }
        } catch (ApiException e) {
            // account does not exists yet
            log.debug("MarketPay Api Exception: {}", e.getError());
        }

        return null;
    }

    /**
     * Construct updateAccountHolderRequest to Adyen from Mirakl shop
     */
    protected Optional<UpdateAccountHolderRequest> updateAccountHolderRequestFromShop(MiraklShop shop, GetAccountHolderResponse getAccountHolderResponse) {
        if (shop.getPaymentInformation() instanceof MiraklIbanBankAccountInformation) {
            MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = (MiraklIbanBankAccountInformation) shop.getPaymentInformation();
            if ((! miraklIbanBankAccountInformation.getIban().isEmpty() && shop.getCurrencyIsoCode() != null) &&
                // if IBAN already exists and is the same then ignore this
                (! isIbanIdentical(miraklIbanBankAccountInformation.getIban(), getAccountHolderResponse))) {
                UpdateAccountHolderRequest updateAccountHolderRequest = new UpdateAccountHolderRequest();
                updateAccountHolderRequest.setAccountHolderCode(shop.getId());
                // create AccountHolderDetails
                AccountHolderDetails accountHolderDetails = new AccountHolderDetails();
                accountHolderDetails.setBankAccountDetails(setBankAccountDetails(shop));
                updateAccountHolderRequest.setAccountHolderDetails(accountHolderDetails);
                return Optional.of(updateAccountHolderRequest);

            }
        }

        log.warn("Unable to create Account holder details, skipping update for shop: {}", shop.getId());
        return Optional.empty();
    }

    /**
     * Check if IBAN is changed
     */
    protected boolean isIbanChanged(GetAccountHolderResponse getAccountHolderResponse, MiraklShop shop) {

        if (shop.getPaymentInformation() instanceof MiraklIbanBankAccountInformation) {
            MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = (MiraklIbanBankAccountInformation) shop.getPaymentInformation();
            if (! miraklIbanBankAccountInformation.getIban().isEmpty()) {
                if (getAccountHolderResponse.getAccountHolderDetails() != null && ! getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().isEmpty()) {
                    if (! miraklIbanBankAccountInformation.getIban().equals(getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().get(0).getIban())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if IBAN is the same as on Adyen side
     */
    protected boolean isIbanIdentical(String iban, GetAccountHolderResponse getAccountHolderResponse) {

        if (getAccountHolderResponse.getAccountHolderDetails() != null && ! getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().isEmpty()) {
            if (iban.equals(getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().get(0).getIban())) {
                return true;
            }
        }
        return false;
    }


    /**
     * Set bank account details
     */
    private List<BankAccountDetail> setBankAccountDetails(MiraklShop shop) {
        BankAccountDetail bankAccountDetail = createBankAccountDetail(shop);
        List<BankAccountDetail> bankAccountDetails = new ArrayList<>();
        bankAccountDetails.add(bankAccountDetail);
        return bankAccountDetails;
    }

    private BankAccountDetail createBankAccountDetail(MiraklShop shop) {
        if (! (shop.getPaymentInformation() instanceof MiraklIbanBankAccountInformation)) {
            log.debug("No IBAN bank account details, not creating bank account detail");
            return null;
        }
        MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = (MiraklIbanBankAccountInformation) shop.getPaymentInformation();

        // create AcountHolderDetails
        AccountHolderDetails accountHolderDetails = new AccountHolderDetails();

        // set BankAccountDetails
        BankAccountDetail bankAccountDetail = new BankAccountDetail();

        // check if PaymentInformation is object MiraklIbanBankAccountInformation
        miraklIbanBankAccountInformation.getIban();
        bankAccountDetail.setIban(miraklIbanBankAccountInformation.getIban());
        bankAccountDetail.setBankCity(miraklIbanBankAccountInformation.getBankCity());
        bankAccountDetail.setBankBicSwift(miraklIbanBankAccountInformation.getBic());
        bankAccountDetail.setCountryCode(getBankCountryFromIban(miraklIbanBankAccountInformation.getIban())); // required field
        bankAccountDetail.setCurrencyCode(shop.getCurrencyIsoCode().toString());


        if (shop.getContactInformation() != null) {
            bankAccountDetail.setOwnerPostalCode(shop.getContactInformation().getZipCode());
            bankAccountDetail.setOwnerHouseNumberOrName(getHouseNumberFromStreet(shop.getContactInformation().getStreet1()));
            bankAccountDetail.setOwnerName(shop.getPaymentInformation().getOwner());
        }

        bankAccountDetail.setPrimaryAccount(true);

        List<BankAccountDetail> bankAccountDetails = new ArrayList<>();
        bankAccountDetails.add(bankAccountDetail);
        accountHolderDetails.setBankAccountDetails(bankAccountDetails);

        return bankAccountDetail;
    }


    /**
     * First two digits of IBAN holds ISO country code
     */
    private String getBankCountryFromIban(String iban) {
        return iban.substring(0, 2);
    }


    /**
     * TODO: implement method to retrieve housenumber from street
     */
    private String getHouseNumberFromStreet(String street) {
        return "1";
    }

    public Integer getMaxUbos() {
        return maxUbos;
    }

    public void setMaxUbos(Integer maxUbos) {
        this.maxUbos = maxUbos;
    }

    /**
     * Get ISO-2 Country Code from ISO-3 Country Code
     */
    protected String getIso2CountryCodeFromIso3(String iso3) {
        if (! iso3.isEmpty()) {
            return countryCodes().get(iso3);
        }
        return null;
    }

    /**
     * Do this on application start-up
     */
    public Map<String, String> countryCodes() {

        Map<String, String> countryCodes = new HashMap<>();
        String[] isoCountries = Locale.getISOCountries();

        for (String country : isoCountries) {
            Locale locale = new Locale("", country);
            countryCodes.put(locale.getISO3Country(), locale.getCountry());
        }
        return countryCodes;
    }

    //ADY-15 temp
    protected void retrieveBankproofAndUpload(ArrayList shopIDs) throws Exception {
        List<MiraklShopDocument> miraklShopDocumentList = retrieveUpdatedDocs(shopIDs);

        for (MiraklShopDocument document : miraklShopDocumentList) {
            if (document.getTypeCode().equals("adyen-bankproof")) {
                FileWrapper content = downloadSelectedDocument(document);
                uploadDocumentToAdyen(DocumentDetail.DocumentTypeEnum.BANK_STATEMENT, content, document.getShopId());
            }
        }
    }

    /**
     * Retrieve documents from Mirakl(S30)
     */
    protected List<MiraklShopDocument> retrieveUpdatedDocs(ArrayList shopIDs) {
        //replace with call to get all documents with updated_since
        MiraklGetShopDocumentsRequest request = new MiraklGetShopDocumentsRequest(shopIDs);
        //        request.getQueryParams().put("updated_since", "2018-01-01");
        return miraklMarketplacePlatformOperatorApiClient.getShopDocuments(request);
    }

    /**
     * Download one document from Mirakl(S31), it will always be one document, this prevents mirakl from returning a zip file, which is not supported on Adyen
     */
    protected FileWrapper downloadSelectedDocument(MiraklShopDocument document) {
        MiraklDownloadShopsDocumentsRequest request = new MiraklDownloadShopsDocumentsRequest();
        ArrayList<String> documentIds = new ArrayList();
        documentIds.add(document.getId());
        request.setDocumentIds(documentIds);
        FileWrapper fileWrapper = miraklMarketplacePlatformOperatorApiClient.downloadShopsDocuments(request);
        return fileWrapper;
    }

    /**
     * Encode document retrieved from Mirakl in Base64 and push it to Adyen, if the document type is BANK_STATEMENT/adyen-bankproof, a bank account is needed
     */
    protected void uploadDocumentToAdyen(DocumentDetail.DocumentTypeEnum documentType, FileWrapper document, String shopId) throws Exception {
        UploadDocumentRequest request = new UploadDocumentRequest();
        request.setAccountHolderCode(shopId);

        //Encode file Base64
        byte[] bytes = toByteArray(document.getFile());
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
        documentDetail.setFilename(document.getFilename());
        documentDetail.setDocumentType(documentType);
        request.setDocumentDetail(documentDetail);
        UploadDocumentResponse response = adyenAccountService.uploadDocument(request);
        log.debug("UploadDocumentResponse: ", response);
    }

    /**
     * Call to Adyen to retrieve the (first)bankaccountUUID
     */
    protected String retrieveBankAccountUUID(String shopID) throws Exception {
        GetAccountHolderRequest getAccountHolderRequest = new GetAccountHolderRequest();
        getAccountHolderRequest.setAccountHolderCode(shopID);
        GetAccountHolderResponse getAccountHolderResponse = adyenAccountService.getAccountHolder(getAccountHolderRequest);
        if (! getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().isEmpty()) {
            return getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().get(0).getBankAccountUUID();
        }
        return null;
    }

}
