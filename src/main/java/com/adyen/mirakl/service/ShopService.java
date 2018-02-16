package com.adyen.mirakl.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.adyen.mirakl.config.AdyenConfiguration;
import com.adyen.mirakl.config.MiraklFrontApiClientFactory;
import com.adyen.mirakl.startup.StartupValidator.CustomMiraklFields;
import com.adyen.model.Name;
import com.adyen.model.marketpay.AccountHolderDetails;
import com.adyen.model.marketpay.BusinessDetails;
import com.adyen.model.marketpay.CreateAccountHolderRequest;
import com.adyen.model.marketpay.CreateAccountHolderRequest.LegalEntityEnum;
import com.adyen.model.marketpay.CreateAccountHolderResponse;
import com.adyen.model.marketpay.ShareholderContact;
import com.adyen.service.Account;
import com.google.common.collect.ImmutableMap;
import com.mirakl.client.mmp.domain.additionalfield.MiraklAdditionalFieldType;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue;
import com.mirakl.client.mmp.domain.shop.MiraklContactInformation;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.front.core.MiraklMarketplacePlatformFrontApi;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;

/**
 * Service class for managing users.
 */
@Service
@Transactional
public class ShopService {
    private final Logger log = LoggerFactory.getLogger(ShopService.class);

    private static Map<String, Name.GenderEnum> CIVILITY_TO_GENDER = ImmutableMap.<String, Name.GenderEnum>builder().put("Mr", Name.GenderEnum.MALE)
                                                                                                                    .put("Mrs", Name.GenderEnum.FEMALE)
                                                                                                                    .put("Miss", Name.GenderEnum.FEMALE)
                                                                                                                    .build();

    private final MiraklFrontApiClientFactory miraklFrontApiClientFactory;

    private final AdyenConfiguration adyenConfiguration;

    public ShopService(MiraklFrontApiClientFactory miraklFrontApiClientFactory, AdyenConfiguration adyenConfiguration) {
        this.miraklFrontApiClientFactory = miraklFrontApiClientFactory;
        this.adyenConfiguration = adyenConfiguration;
    }

    /**
     * Not activated users should be automatically deleted after 3 days.
     * <p>
     * This is scheduled to get fired everyday, at 01:00 (am).
     */
    @Scheduled(cron = "0 * * * * ?")
    public void retrievedUpdatedShops() {
        MiraklShops miraklShops = getUpdatedShops();
        Account adyenAccountService = adyenConfiguration.createAccountService();

        for (MiraklShop shop : miraklShops.getShops()) {
            try {
                CreateAccountHolderRequest createAccountHolderRequest = createAccountHolderRequestFromShop(shop);
                CreateAccountHolderResponse response = adyenAccountService.createAccountHolder(createAccountHolderRequest);
            } catch (Exception e) {
                //todo: handle
            }
        }
    }

    private MiraklShops getUpdatedShops() {
        MiraklMarketplacePlatformFrontApi client = miraklFrontApiClientFactory.createMiraklMarketplacePlatformFrontApiClient();

        MiraklGetShopsRequest request = new MiraklGetShopsRequest();
        return client.getShops(request);
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
        BusinessDetails businessDetails = new BusinessDetails();
        List<ShareholderContact> shareholders = new ArrayList<>();

        shareholders.add(createShareholderContactFromShop(shop));
        businessDetails.setShareholders(shareholders);
        accountHolderDetails.setBusinessDetails(businessDetails);
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

    private ShareholderContact createShareholderContactFromShop(MiraklShop shop) {
        ShareholderContact shareholderContact = new ShareholderContact();
        MiraklContactInformation contactInformation = shop.getContactInformation();   //todo: NPE check

        shareholderContact.setEmail(contactInformation.getEmail());

        Name name = new Name();
        name.setFirstName(contactInformation.getFirstname());
        name.setLastName(contactInformation.getLastname());
        if (CIVILITY_TO_GENDER.containsKey(contactInformation.getCivility())) {
            name.setGender(CIVILITY_TO_GENDER.get(contactInformation.getCivility()));
        }
        shareholderContact.setName(name);
        return shareholderContact;
    }

    private boolean isListWithCode(MiraklAdditionalFieldValue additionalFieldValue, CustomMiraklFields field) {
        return MiraklAdditionalFieldType.LIST.equals(additionalFieldValue.getFieldType()) && field.toString().equalsIgnoreCase(additionalFieldValue.getCode());
    }
}
