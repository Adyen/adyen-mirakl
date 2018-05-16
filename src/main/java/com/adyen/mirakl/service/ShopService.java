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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import com.adyen.mirakl.domain.StreetDetails;
import com.adyen.mirakl.service.util.IsoUtil;
import com.adyen.mirakl.service.util.MiraklDataExtractionUtil;
import com.adyen.model.Address;
import com.adyen.model.Amount;
import com.adyen.model.Name;
import com.adyen.model.marketpay.AccountHolderDetails;
import com.adyen.model.marketpay.BankAccountDetail;
import com.adyen.model.marketpay.BusinessDetails;
import com.adyen.model.marketpay.CreateAccountHolderRequest;
import com.adyen.model.marketpay.CreateAccountHolderRequest.LegalEntityEnum;
import com.adyen.model.marketpay.CreateAccountHolderResponse;
import com.adyen.model.marketpay.DeleteBankAccountRequest;
import com.adyen.model.marketpay.DeleteBankAccountResponse;
import com.adyen.model.marketpay.ErrorFieldType;
import com.adyen.model.marketpay.GetAccountHolderRequest;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.IndividualDetails;
import com.adyen.model.marketpay.PersonalData;
import com.adyen.model.marketpay.ShareholderContact;
import com.adyen.model.marketpay.UpdateAccountHolderRequest;
import com.adyen.model.marketpay.UpdateAccountHolderResponse;
import com.adyen.model.marketpay.notification.CompensateNegativeBalanceNotificationRecord;
import com.adyen.service.Account;
import com.adyen.service.exception.ApiException;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.shop.MiraklContactInformation;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.operator.domain.invoice.MiraklCreateManualAccountingDocument;
import com.mirakl.client.mmp.operator.domain.invoice.MiraklCreatedManualAccountingDocuments;
import com.mirakl.client.mmp.operator.domain.invoice.MiraklManualAccountingDocumentLine;
import com.mirakl.client.mmp.operator.domain.invoice.MiraklManualAccountingDocumentType;
import com.mirakl.client.mmp.operator.request.payment.invoice.MiraklCreateManualAccountingDocumentRequest;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;

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

    @Resource
    private ShareholderMappingService shareholderMappingService;

    @Resource
    private UboService uboService;

    @Resource
    private InvalidFieldsNotificationService invalidFieldsNotificationService;

    @Resource
    private Map<String, Pattern> houseNumberPatterns;

    @Resource
    private DocService docService;

    @Value("${payoutService.liableAccountCode}")
    private String liableAccountCode;

    public void processUpdatedShops() {
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
                log.error("MarketPay Api Exception: {}, {}. For the Shop: {}", e.getError(), e, shop.getId());
            } catch (Exception e) {
                log.error("Exception: {}, {}. For the Shop: {}", e.getMessage(), e, shop.getId());
            }
        }
        shops.forEach(shop -> docService.retryDocumentsForShop(shop.getId()));
        deltaService.updateShopDelta(beforeProcessing);
    }

    private void processCreateAccountHolder(final MiraklShop shop) throws Exception {
        CreateAccountHolderRequest createAccountHolderRequest = createAccountHolderRequestFromShop(shop);
        CreateAccountHolderResponse response = adyenAccountService.createAccountHolder(createAccountHolderRequest);
        shareholderMappingService.updateShareholderMapping(response, shop);
        log.debug("CreateAccountHolderResponse: {}", response);
        if (! CollectionUtils.isEmpty(response.getInvalidFields())) {
            final String invalidFields = response.getInvalidFields().stream().map(ErrorFieldType::toString).collect(Collectors.joining(","));
            log.warn("Invalid fields when trying to create shop {}: {}", shop.getId(), invalidFields);
            invalidFieldsNotificationService.handleErrorsInResponse(shop, response.getInvalidFields());
        }
    }

    private void processUpdateAccountHolder(final MiraklShop shop, final GetAccountHolderResponse getAccountHolderResponse) throws Exception {
        UpdateAccountHolderRequest updateAccountHolderRequest = updateAccountHolderRequestFromShop(shop, getAccountHolderResponse);

        UpdateAccountHolderResponse response = adyenAccountService.updateAccountHolder(updateAccountHolderRequest);
        shareholderMappingService.updateShareholderMapping(response, shop);
        log.debug("UpdateAccountHolderResponse: {}", response);

        if (! CollectionUtils.isEmpty(response.getInvalidFields())) {
            final String invalidFields = response.getInvalidFields().stream().map(ErrorFieldType::toString).collect(Collectors.joining(","));
            log.warn("Invalid fields when trying to update shop {}: {}", shop.getId(), invalidFields);
            invalidFieldsNotificationService.handleErrorsInResponse(shop, response.getInvalidFields());
        }

        cleanUpBankAccounts(getAccountHolderResponse, shop);
    }

    /**
     * Remove bank accounts that don't match the shop's IBAN
     *
     * @return true if there were accounts removed
     */
    protected boolean cleanUpBankAccounts(GetAccountHolderResponse getAccountHolderResponse, MiraklShop shop) throws Exception {

        String existingBankAccountDetailUuid = getBankAccountDetailFromShop(getAccountHolderResponse.getAccountHolderDetails(), shop).map(BankAccountDetail::getBankAccountUUID).orElse(null);
        List<String> uuids = getAccountHolderResponse.getAccountHolderDetails()
                                                     .getBankAccountDetails()
                                                     .stream()
                                                     .filter(b -> ! b.getBankAccountUUID().equals(existingBankAccountDetailUuid))
                                                     .map(BankAccountDetail::getBankAccountUUID)
                                                     .collect(Collectors.toList());

        if (! uuids.isEmpty()) {
            DeleteBankAccountResponse deleteBankAccountResponse = adyenAccountService.deleteBankAccount(deleteBankAccountRequest(getAccountHolderResponse.getAccountHolderCode(), uuids));
            log.debug("DeleteBankAccountResponse: {}", deleteBankAccountResponse);
        }

        return ! uuids.isEmpty();
    }

    /**
     * Construct DeleteBankAccountRequest to remove outdated iban bankaccounts
     */
    protected DeleteBankAccountRequest deleteBankAccountRequest(String accountHolderCode, List<String> uuids) {
        DeleteBankAccountRequest deleteBankAccountRequest = new DeleteBankAccountRequest();
        deleteBankAccountRequest.accountHolderCode(accountHolderCode);
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
            log.debug("getShops request since: " + miraklGetShopsRequest.getUpdatedSince());
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
        LegalEntityEnum legalEntity = MiraklDataExtractionUtil.getLegalEntityFromShop(shop.getAdditionalFieldValues());
        createAccountHolderRequest.setLegalEntity(legalEntity);

        // Set AccountHolderDetails
        AccountHolderDetails accountHolderDetails = new AccountHolderDetails();
        Optional<BankAccountDetail> bankAccountDetailOptional = createBankAccountDetail(shop);
        bankAccountDetailOptional.ifPresent(accountHolderDetails::addBankAccountDetail);

        updateDetailsFromShop(accountHolderDetails, shop, null);

        // Set email
        MiraklContactInformation contactInformation = getContactInformationFromShop(shop);
        accountHolderDetails.setEmail(contactInformation.getEmail());
        createAccountHolderRequest.setAccountHolderDetails(accountHolderDetails);

        return createAccountHolderRequest;
    }

    private MiraklContactInformation getContactInformationFromShop(MiraklShop shop) {
        return Optional.of(shop.getContactInformation()).orElseThrow(() -> new RuntimeException("Contact information not found"));
    }

    private Address createAddressFromShop(MiraklShop shop) {
        MiraklContactInformation contactInformation = getContactInformationFromShop(shop);
        if (contactInformation != null && ! StringUtils.isEmpty(contactInformation.getCountry())) {

            Address address = new Address();
            address.setPostalCode(contactInformation.getZipCode());
            address.setCountry(IsoUtil.getIso2CountryCodeFromIso3(contactInformation.getCountry()));
            address.setCity(contactInformation.getCity());

            StreetDetails streetDetails = StreetDetails.createStreetDetailsFromSingleLine(StreetDetails.extractHouseNumberOrNameFromAdditionalFields(shop.getAdditionalFieldValues()),
                                                                                          contactInformation.getStreet1(),
                                                                                          houseNumberPatterns.get(IsoUtil.getIso2CountryCodeFromIso3(shop.getContactInformation().getCountry())));
            address.setStreet(streetDetails.getStreetName());
            address.setHouseNumberOrName(streetDetails.getHouseNumberOrName());

            return address;
        }
        return null;
    }

    private BusinessDetails addBusinessDetailsFromShop(final MiraklShop shop, final GetAccountHolderResponse existingAccountHolder) {
        BusinessDetails businessDetails = new BusinessDetails();

        if (shop.getProfessionalInformation() != null) {
            if (StringUtils.isNotEmpty(shop.getProfessionalInformation().getCorporateName())) {
                businessDetails.setLegalBusinessName(shop.getProfessionalInformation().getCorporateName());
            }
            if (StringUtils.isNotEmpty(shop.getProfessionalInformation().getTaxIdentificationNumber())) {
                businessDetails.setTaxId(shop.getProfessionalInformation().getTaxIdentificationNumber());
            }
        }
        List<ShareholderContact> shareholders = uboService.extractUbos(shop, existingAccountHolder);
        if (shareholders.isEmpty()) {
            log.info("No shareholder data for shop {}", shop.getId());
            throw new IllegalArgumentException("No shareholder data found");
        }
        businessDetails.setShareholders(shareholders);
        return businessDetails;
    }

    private IndividualDetails createIndividualDetailsFromShop(MiraklShop shop) {
        IndividualDetails individualDetails = new IndividualDetails();

        shop.getAdditionalFieldValues()
            .stream()
            .filter(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue.class::isInstance)
            .map(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue.class::cast)
            .filter(fieldValue -> "adyen-individual-dob".equals(fieldValue.getCode()))
            .findAny()
            .ifPresent(value -> {
                PersonalData personalData = new PersonalData();
                personalData.setDateOfBirth(value.getValue());
                individualDetails.setPersonalData(personalData);
            });

        MiraklContactInformation contactInformation = getContactInformationFromShop(shop);

        Name name = new Name();
        name.setFirstName(contactInformation.getFirstname());
        name.setLastName(contactInformation.getLastname());
        name.setGender(UboService.CIVILITY_TO_GENDER.getOrDefault(contactInformation.getCivility().toUpperCase(), Name.GenderEnum.UNKNOWN));
        individualDetails.setName(name);
        return individualDetails;
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
            log.debug("MarketPay Api Exception: {}. Shop ", e.getError());
        }

        return null;
    }

    private Optional<String> getIbanFromShop(MiraklShop shop) {
        if (shop == null || ! (shop.getPaymentInformation() instanceof MiraklIbanBankAccountInformation)) {
            return Optional.empty();
        }

        MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = (MiraklIbanBankAccountInformation) shop.getPaymentInformation();
        if (StringUtils.isEmpty(miraklIbanBankAccountInformation.getIban())) {
            return Optional.empty();
        }

        return Optional.of(miraklIbanBankAccountInformation.getIban());
    }

    private Optional<BankAccountDetail> getBankAccountDetailFromShop(AccountHolderDetails accountHolderDetails, MiraklShop shop) {
        Optional<String> ibanOptional = getIbanFromShop(shop);
        if (! ibanOptional.isPresent() || accountHolderDetails == null || accountHolderDetails.getBankAccountDetails() == null || accountHolderDetails.getBankAccountDetails().isEmpty()) {
            return Optional.empty();
        }

        return accountHolderDetails.getBankAccountDetails().stream().filter(e -> StringUtils.equals(e.getIban(), ibanOptional.get())).findFirst();
    }

    /**
     * Construct updateAccountHolderRequest to Adyen from Mirakl shop
     */
    protected UpdateAccountHolderRequest updateAccountHolderRequestFromShop(MiraklShop shop, GetAccountHolderResponse existingAccountHolder) {

        UpdateAccountHolderRequest updateAccountHolderRequest = new UpdateAccountHolderRequest();
        updateAccountHolderRequest.setAccountHolderCode(shop.getId());

        AccountHolderDetails accountHolderDetails = new AccountHolderDetails();
        updateAccountHolderRequest.setAccountHolderDetails(accountHolderDetails);

        // Update bank account details
        Optional<BankAccountDetail> bankAccountDetail = createBankAccountDetail(shop);

        if (bankAccountDetail.isPresent()) {
            Optional<BankAccountDetail> existingBankAccountDetail = getBankAccountDetailFromShop(existingAccountHolder.getAccountHolderDetails(), shop);
            // use the existing uuid if iban already exists
            existingBankAccountDetail.ifPresent(b -> bankAccountDetail.get().setBankAccountUUID(b.getBankAccountUUID()));

            // add BankAccountDetails in case of new or changed bank
            if (! existingBankAccountDetail.isPresent() || ! existingBankAccountDetail.get().equals(bankAccountDetail.get())) {
                accountHolderDetails.addBankAccountDetail(bankAccountDetail.get());
            }
        }

        updateDetailsFromShop(accountHolderDetails, shop, existingAccountHolder);

        return updateAccountHolderRequest;
    }

    private AccountHolderDetails updateDetailsFromShop(AccountHolderDetails accountHolderDetails, MiraklShop shop, GetAccountHolderResponse existingAccountHolder) {
        LegalEntityEnum legalEntity = MiraklDataExtractionUtil.getLegalEntityFromShop(shop.getAdditionalFieldValues());

        if (LegalEntityEnum.INDIVIDUAL == legalEntity) {
            IndividualDetails individualDetails = createIndividualDetailsFromShop(shop);
            accountHolderDetails.setIndividualDetails(individualDetails);
        } else if (LegalEntityEnum.BUSINESS == legalEntity) {
            BusinessDetails businessDetails = addBusinessDetailsFromShop(shop, existingAccountHolder);
            accountHolderDetails.setBusinessDetails(businessDetails);
        } else {
            throw new IllegalArgumentException(legalEntity.toString() + " not supported");
        }

        accountHolderDetails.setAddress(createAddressFromShop(shop));

        return accountHolderDetails;
    }

    private Optional<BankAccountDetail> createBankAccountDetail(MiraklShop shop) {
        if (shop.getPaymentInformation() == null || ! (shop.getPaymentInformation() instanceof MiraklIbanBankAccountInformation)) {
            log.info("No MiraklIbanBankAccountInformation found for shop: {}", shop.getId());
            return Optional.empty();
        }
        MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = (MiraklIbanBankAccountInformation) shop.getPaymentInformation();

        if (miraklIbanBankAccountInformation.getIban().isEmpty() || shop.getCurrencyIsoCode() == null) {
            log.info("Empty IBAN or currency for shop: {}", shop.getId());
            return Optional.empty();
        }

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
            StreetDetails streetDetails = StreetDetails.createStreetDetailsFromSingleLine(StreetDetails.extractHouseNumberOrNameFromAdditionalFields(shop.getAdditionalFieldValues()),
                                                                                          shop.getContactInformation().getStreet1(),
                                                                                          houseNumberPatterns.get(IsoUtil.getIso2CountryCodeFromIso3(shop.getContactInformation().getCountry())));
            bankAccountDetail.setOwnerStreet(streetDetails.getStreetName());
            bankAccountDetail.setOwnerHouseNumberOrName(streetDetails.getHouseNumberOrName());

            bankAccountDetail.setOwnerPostalCode(shop.getContactInformation().getZipCode());
            bankAccountDetail.setOwnerCity(shop.getContactInformation().getCity());
            bankAccountDetail.setOwnerName(shop.getPaymentInformation().getOwner());
        }

        bankAccountDetail.setPrimaryAccount(true);
        accountHolderDetails.addBankAccountDetail(bankAccountDetail);

        return Optional.of(bankAccountDetail);
    }


    /**
     * First two digits of IBAN holds ISO country code
     */
    private String getBankCountryFromIban(String iban) {
        return iban.substring(0, 2);
    }

    public void setHouseNumberPatterns(final Map<String, Pattern> houseNumberPatterns) {
        this.houseNumberPatterns = houseNumberPatterns;
    }

    /**
     * IV03: Create a manual accounting document
     */
    public MiraklCreatedManualAccountingDocuments processCompensateNegativeBalance(CompensateNegativeBalanceNotificationRecord compensateNegativeBalanceNotificationRecord, String pspReference) throws Exception {
        final String accountCode = compensateNegativeBalanceNotificationRecord.getAccountCode();
        Amount amount = compensateNegativeBalanceNotificationRecord.getAmount();
        Date transferDate = compensateNegativeBalanceNotificationRecord.getTransferDate();
        String shopId = retrieveShopIdFromAccountCode(accountCode);

        MiraklCreateManualAccountingDocument miraklCreateManualAccountingDocument = new MiraklCreateManualAccountingDocument();
        miraklCreateManualAccountingDocument.setEmissionDate(transferDate);
        miraklCreateManualAccountingDocument.setIssued(true);

        MiraklManualAccountingDocumentLine miraklManualAccountingDocumentLine = new MiraklManualAccountingDocumentLine();
        miraklManualAccountingDocumentLine.setAmount(amount.getDecimalValue().negate());
        miraklManualAccountingDocumentLine.setDescription("Compensate negative balance from the account: " + liableAccountCode + " to the account: " + accountCode + " pspReference: " + pspReference);
        miraklManualAccountingDocumentLine.setQuantity(1);
        List<String> taxCodes = new ArrayList<>();
        taxCodes.add("TAXZERO");
        miraklManualAccountingDocumentLine.setTaxCodes(taxCodes);
        List<MiraklManualAccountingDocumentLine> miraklManualAccountingDocumentLineList = new ArrayList<>();
        miraklManualAccountingDocumentLineList.add(miraklManualAccountingDocumentLine);
        miraklCreateManualAccountingDocument.setLines(miraklManualAccountingDocumentLineList);

        miraklCreateManualAccountingDocument.setShopId(Long.valueOf(shopId));
        miraklCreateManualAccountingDocument.setType(MiraklManualAccountingDocumentType.CREDIT);

        List<MiraklCreateManualAccountingDocument> miraklCreateManualAccountingDocumentList = new ArrayList<>();
        miraklCreateManualAccountingDocumentList.add(miraklCreateManualAccountingDocument);

        MiraklCreateManualAccountingDocumentRequest request = new MiraklCreateManualAccountingDocumentRequest(miraklCreateManualAccountingDocumentList);

        return miraklMarketplacePlatformOperatorApiClient.createManualAccountingDocument(request);
    }

    protected String retrieveShopIdFromAccountCode(String accountCode) {
        GetAccountHolderRequest getAccountHolderRequest = new GetAccountHolderRequest();
        getAccountHolderRequest.setAccountCode(accountCode);
        try {
            GetAccountHolderResponse getAccountHolderResponse = adyenAccountService.getAccountHolder(getAccountHolderRequest);
            if (! getAccountHolderResponse.getAccountHolderCode().isEmpty()) {
                return getAccountHolderResponse.getAccountHolderCode();
            }
        } catch (ApiException e) {
            log.error("MarketPay Api Exception: {}, {}. For the AccountCode: {}", e.getError(), e, accountCode);
        } catch (Exception e) {
            log.error("Exception: {}, {}. For the AccountCode: {}", e.getMessage(), e, accountCode);
        }
        return null;
    }
}
