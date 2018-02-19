package com.adyen.mirakl.service;

import java.util.*;
import javax.annotation.Resource;

import com.adyen.model.marketpay.*;
import com.adyen.service.exception.ApiException;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.adyen.mirakl.startup.StartupValidator.CustomMiraklFields;
import com.adyen.model.Name;
import com.adyen.model.marketpay.CreateAccountHolderRequest.LegalEntityEnum;
import com.adyen.service.Account;
import com.google.common.collect.ImmutableMap;
import com.mirakl.client.mmp.domain.additionalfield.MiraklAdditionalFieldType;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue;
import com.mirakl.client.mmp.domain.shop.MiraklContactInformation;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
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

    //    @Scheduled(cron = "${application.shopUpdaterCron}")
    public void retrievedUpdatedShops() {
        MiraklShops miraklShops = getUpdatedShops();

        log.debug("Retrieved shops: " + miraklShops.getShops().size());
        for (MiraklShop shop : miraklShops.getShops()) {
            try {
                if (accountHolderExists(shop, adyenAccountService)) {
                    UpdateAccountHolderRequest updateAccountHolderRequest = updateAccountHolderRequestFromShop(shop);
                    UpdateAccountHolderResponse response = adyenAccountService.updateAccountHolder(updateAccountHolderRequest);
                    log.debug("UpdateAccountHolderResponse: " + response);
                } else {
                    CreateAccountHolderRequest createAccountHolderRequest = createAccountHolderRequestFromShop(shop);
                    CreateAccountHolderResponse response = adyenAccountService.createAccountHolder(createAccountHolderRequest);
                    log.debug("CreateAccountHolderResponse: " + response);
                }
            } catch (ApiException e) {
                // account does not exists yet
                log.warn(e.getError().getMessage());
            } catch (Exception e) {
                log.warn("MP exception: " + e.getMessage());
            }
        }
    }

    private MiraklShops getUpdatedShops() {
        MiraklGetShopsRequest request = new MiraklGetShopsRequest();
        return miraklMarketplacePlatformOperatorApiClient.getShops(request);
    }

    public CreateAccountHolderRequest createAccountHolderRequestFromShop(MiraklShop shop) {
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
            .filter(field -> isListWithCode(field, CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE))
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
        }
        individualDetails.setName(name);
        return individualDetails;
    }

    private boolean isListWithCode(MiraklAdditionalFieldValue additionalFieldValue, CustomMiraklFields field) {
        return MiraklAdditionalFieldType.LIST.equals(additionalFieldValue.getFieldType()) && field.toString().equalsIgnoreCase(additionalFieldValue.getCode());
    }

    /**
     * Check if AccountHolder already exists in Adyen
     *
     * @param shop
     * @param adyenAccountService
     * @return
     */
    private boolean accountHolderExists(MiraklShop shop, Account adyenAccountService) throws Exception {

        // lookup accountHolder in Adyen
        GetAccountHolderRequest getAccountHolderRequest = new GetAccountHolderRequest();
        getAccountHolderRequest.setAccountHolderCode(shop.getId());

        try {
            GetAccountHolderResponse getAccountHolderResponse = adyenAccountService.getAccountHolder(getAccountHolderRequest);
            if (!getAccountHolderResponse.getAccountHolderCode().isEmpty()) {
                return true;
            }

        } catch (ApiException e) {
            // account does not exists yet
            log.info(e.getError().getMessage());
        }

        return false;
    }

    /**
     * Construct updateAccountHolderRequest to Adyen from Mirakl shop
     *
     * @param shop
     * @return
     */
    protected UpdateAccountHolderRequest updateAccountHolderRequestFromShop(MiraklShop shop) {

        UpdateAccountHolderRequest updateAccountHolderRequest = new UpdateAccountHolderRequest();
        updateAccountHolderRequest.setAccountHolderCode(shop.getId());

        // create AcountHolderDetails
        AccountHolderDetails accountHolderDetails = new AccountHolderDetails();

        // set BankAccountDetails
        BankAccountDetail bankAccountDetail = new BankAccountDetail();

        // check if PaymentInformation is object MiraklIbanBankAccountInformation
        MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = (MiraklIbanBankAccountInformation) shop.getPaymentInformation();
        miraklIbanBankAccountInformation.getIban();
        bankAccountDetail.setIban(miraklIbanBankAccountInformation.getIban());
        bankAccountDetail.setBankBicSwift(miraklIbanBankAccountInformation.getBic());
        bankAccountDetail.setCountryCode(getBankCountryFromShop(shop)); // required field
        bankAccountDetail.setCurrencyCode(shop.getCurrencyIsoCode().toString());

        bankAccountDetail.setOwnerPostalCode(miraklIbanBankAccountInformation.getBankZip());
        bankAccountDetail.setOwnerHouseNumberOrName(getHouseNumberFromStreet(miraklIbanBankAccountInformation.getBankStreet()));
        bankAccountDetail.setOwnerName(miraklIbanBankAccountInformation.getOwner());

        List<BankAccountDetail> bankAccountDetails = new ArrayList<BankAccountDetail>();
        bankAccountDetails.add(bankAccountDetail);
        accountHolderDetails.setBankAccountDetails(bankAccountDetails);

        updateAccountHolderRequest.setAccountHolderDetails(accountHolderDetails);


        return updateAccountHolderRequest;
    }

    private String getBankCountryFromShop(MiraklShop shop) {
        MiraklValueListAdditionalFieldValue additionalFieldValue = (MiraklValueListAdditionalFieldValue) shop.getAdditionalFieldValues()
            .stream()
            .filter(field -> isListWithCode(field, CustomMiraklFields.ADYEN_BANK_COUNTRY))
            .findAny()
            .orElseThrow(() -> new RuntimeException("Adyen Bank Country not found"));

        return additionalFieldValue.getValue();
    }


    /**
     * TODO: implement method to retrieve housenumber from street
     *
     * @param street
     * @return
     */
    private String getHouseNumberFromStreet(String street) {
        return "1";
    }
}
