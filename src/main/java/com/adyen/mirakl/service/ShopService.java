package com.adyen.mirakl.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.adyen.mirakl.startup.MiraklStartupValidator;
import com.adyen.model.Name;
import com.adyen.model.marketpay.AccountHolderDetails;
import com.adyen.model.marketpay.BankAccountDetail;
import com.adyen.model.marketpay.CreateAccountHolderRequest;
import com.adyen.model.marketpay.CreateAccountHolderRequest.LegalEntityEnum;
import com.adyen.model.marketpay.CreateAccountHolderResponse;
import com.adyen.model.marketpay.DeleteBankAccountRequest;
import com.adyen.model.marketpay.DeleteBankAccountResponse;
import com.adyen.model.marketpay.GetAccountHolderRequest;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.IndividualDetails;
import com.adyen.model.marketpay.UpdateAccountHolderRequest;
import com.adyen.model.marketpay.UpdateAccountHolderResponse;
import com.adyen.service.Account;
import com.adyen.service.exception.ApiException;
import com.google.common.collect.ImmutableMap;
import com.mirakl.client.mmp.domain.additionalfield.MiraklAdditionalFieldType;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue;
import com.mirakl.client.mmp.domain.shop.MiraklContactInformation;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;

@Service
@Transactional
public class ShopService {
    private final Logger log = LoggerFactory.getLogger(ShopService.class);

    private static Map<String, Name.GenderEnum> CIVILITY_TO_GENDER = ImmutableMap.<String, Name.GenderEnum>builder().put("Mr", Name.GenderEnum.MALE)
                                                                                                                    .put("Mrs", Name.GenderEnum.FEMALE)
                                                                                                                    .put("Miss", Name.GenderEnum.FEMALE)
                                                                                                                    .build();

    @Resource
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient;

    @Resource
    private Account adyenAccountService;

    @Scheduled(cron = "${application.shopUpdaterCron}")
    public void retrieveUpdatedShops() {
        List<MiraklShop> shops = getUpdatedShops();

        log.debug("Retrieved shops: " + shops.size());
        for (MiraklShop shop : shops) {
            try {
                GetAccountHolderResponse getAccountHolderResponse = getAccountHolderFromShop(shop);
                if (getAccountHolderResponse != null) {
                    UpdateAccountHolderRequest updateAccountHolderRequest = updateAccountHolderRequestFromShop(shop, getAccountHolderResponse);
                    UpdateAccountHolderResponse response = adyenAccountService.updateAccountHolder(updateAccountHolderRequest);
                    log.debug("UpdateAccountHolderResponse: " + response);

                    // if IBAN has changed remove the old one
                    if (isIbanChanged(getAccountHolderResponse, shop)) {
                        DeleteBankAccountResponse deleteBankAccountResponse = adyenAccountService.deleteBankAccount(deleteBankAccountRequest(getAccountHolderResponse));
                        log.debug("DeleteBankAccountResponse: " + deleteBankAccountResponse);
                    }
                } else {
                    CreateAccountHolderRequest createAccountHolderRequest = createAccountHolderRequestFromShop(shop);
                    CreateAccountHolderResponse response = adyenAccountService.createAccountHolder(createAccountHolderRequest);
                    log.debug("CreateAccountHolderResponse: " + response);
                }
            } catch (ApiException e) {
                // account does not exists yet
                log.warn("MarketPay Api Exception: " + e.getError());
            } catch (Exception e) {
                log.warn("Exception: " + e.getMessage());
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
            miraklGetShopsRequest.setPaginate(false);
            miraklGetShopsRequest.setOffset(offset);

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

        if (LegalEntityEnum.INDIVIDUAL.equals(legalEntity)) {
            IndividualDetails individualDetails = createIndividualDetailsFromShop(shop);
            accountHolderDetails.setIndividualDetails(individualDetails);
        } else {
            throw new RuntimeException(legalEntity.toString() + " not supported");
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

    private IndividualDetails createIndividualDetailsFromShop(MiraklShop shop) {
        IndividualDetails individualDetails = new IndividualDetails();

        MiraklContactInformation contactInformation = getContactInformationFromShop(shop);

        Name name = new Name();
        name.setFirstName(contactInformation.getFirstname());
        name.setLastName(contactInformation.getLastname());
        if (CIVILITY_TO_GENDER.containsKey(contactInformation.getCivility())) {
            name.setGender(CIVILITY_TO_GENDER.get(contactInformation.getCivility()));
        } else {
            name.setGender(Name.GenderEnum.UNKNOWN);
        }
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
            log.debug("MarketPay Api Exception: " + e.getError());
        }

        return null;
    }

    /**
     * Construct updateAccountHolderRequest to Adyen from Mirakl shop
     */
    protected UpdateAccountHolderRequest updateAccountHolderRequestFromShop(MiraklShop shop, GetAccountHolderResponse getAccountHolderResponse) {
        UpdateAccountHolderRequest updateAccountHolderRequest = new UpdateAccountHolderRequest();
        updateAccountHolderRequest.setAccountHolderCode(shop.getId());

        // create AcountHolderDetails
        AccountHolderDetails accountHolderDetails = new AccountHolderDetails();

        if (shop.getPaymentInformation() instanceof MiraklIbanBankAccountInformation) {
            MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = (MiraklIbanBankAccountInformation) shop.getPaymentInformation();
            if (! miraklIbanBankAccountInformation.getIban().isEmpty() && shop.getCurrencyIsoCode() != null) {
                // if IBAN already exists and is the same then ignore this
                if (! isIbanIdentical(miraklIbanBankAccountInformation.getIban(), getAccountHolderResponse)) {
                    accountHolderDetails.setBankAccountDetails(setBankAccountDetails(miraklIbanBankAccountInformation, shop));
                }
            }
        }
        updateAccountHolderRequest.setAccountHolderDetails(accountHolderDetails);
        return updateAccountHolderRequest;
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
    private List<BankAccountDetail> setBankAccountDetails(MiraklIbanBankAccountInformation miraklIbanBankAccountInformation, MiraklShop shop) {
        BankAccountDetail bankAccountDetail = createBankAccountDetail(miraklIbanBankAccountInformation, shop);
        List<BankAccountDetail> bankAccountDetails = new ArrayList<BankAccountDetail>();
        bankAccountDetails.add(bankAccountDetail);
        return bankAccountDetails;
    }

    private BankAccountDetail createBankAccountDetail(MiraklIbanBankAccountInformation miraklIbanBankAccountInformation, MiraklShop shop) {
        // create AcountHolderDetails
        AccountHolderDetails accountHolderDetails = new AccountHolderDetails();

        // set BankAccountDetails
        BankAccountDetail bankAccountDetail = new BankAccountDetail();

        // check if PaymentInformation is object MiraklIbanBankAccountInformation
        miraklIbanBankAccountInformation.getIban();
        bankAccountDetail.setIban(miraklIbanBankAccountInformation.getIban());
        bankAccountDetail.setBankBicSwift(miraklIbanBankAccountInformation.getBic());
        bankAccountDetail.setCountryCode(getBankCountryFromIban(miraklIbanBankAccountInformation.getIban())); // required field
        bankAccountDetail.setCurrencyCode(shop.getCurrencyIsoCode().toString());


        if (shop.getContactInformation() != null) {
            bankAccountDetail.setOwnerPostalCode(shop.getContactInformation().getZipCode());
            bankAccountDetail.setOwnerHouseNumberOrName(getHouseNumberFromStreet(shop.getContactInformation().getStreet1()));
            bankAccountDetail.setOwnerName(shop.getContactInformation().getFirstname().concat(" ").concat(shop.getContactInformation().getLastname()));
        }

        bankAccountDetail.setPrimaryAccount(true);

        List<BankAccountDetail> bankAccountDetails = new ArrayList<BankAccountDetail>();
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
}
