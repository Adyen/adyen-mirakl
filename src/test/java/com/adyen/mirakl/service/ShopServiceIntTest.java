package com.adyen.mirakl.service;

import java.util.ArrayList;
import java.util.List;

import com.adyen.model.marketpay.UpdateAccountHolderRequest;
import com.mirakl.client.mmp.domain.common.currency.MiraklIsoCurrencyCode;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import com.adyen.mirakl.AdyenMiraklConnectorApp;
import com.adyen.mirakl.startup.StartupValidator;
import com.adyen.model.Name;
import com.adyen.model.marketpay.CreateAccountHolderRequest;
import com.adyen.model.marketpay.IndividualDetails;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.shop.MiraklContactInformation;
import com.mirakl.client.mmp.domain.shop.MiraklShop;

import static com.adyen.mirakl.startup.StartupValidator.AdyenLegalEntityType.INDIVIDUAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test class for the UserResource REST controller.
 *
 * @see UserService
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = AdyenMiraklConnectorApp.class)
@Transactional
public class ShopServiceIntTest {
    @Autowired
    private ShopService shopService;

    @Test
    public void testCreateAHRequest() {
        MiraklShop shop = new MiraklShop();
        List<MiraklAdditionalFieldValue> additionalFields = new ArrayList<>();
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(StartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE));
        additionalField.setValue(INDIVIDUAL.toString());

        MiraklContactInformation contactInformation = new MiraklContactInformation();
        contactInformation.setEmail("email");
        contactInformation.setFirstname("firstName");
        contactInformation.setLastname("lastName");
        contactInformation.setCivility("Mrs");
        shop.setContactInformation(contactInformation);

        additionalFields.add(additionalField);
        shop.setAdditionalFieldValues(additionalFields);
        shop.setId("id");
        CreateAccountHolderRequest request = shopService.createAccountHolderRequestFromShop(shop);

        assertEquals("id", request.getAccountHolderCode());
        assertEquals(CreateAccountHolderRequest.LegalEntityEnum.INDIVIDUAL, request.getLegalEntity());
        assertNotNull(request.getAccountHolderDetails().getIndividualDetails());
        IndividualDetails individualDetails = request.getAccountHolderDetails().getIndividualDetails();
        assertEquals("firstName", individualDetails.getName().getFirstName());
        assertEquals("lastName", individualDetails.getName().getLastName());
        assertEquals(Name.GenderEnum.FEMALE, individualDetails.getName().getGender());
    }

    @Test
    public void testUpdateAccountHolderRequest() {


        MiraklShop shop = new MiraklShop();
        shop.setId("id");
        shop.setCurrencyIsoCode(MiraklIsoCurrencyCode.EUR);

        List<MiraklAdditionalFieldValue> additionalFields = new ArrayList<>();
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(StartupValidator.CustomMiraklFields.ADYEN_BANK_COUNTRY));
        additionalField.setValue("GB");
        additionalFields.add(additionalField);
        shop.setAdditionalFieldValues(additionalFields);

        MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = new MiraklIbanBankAccountInformation();

        miraklIbanBankAccountInformation.setOwner("Owner");
        miraklIbanBankAccountInformation.setIban("IBAN");
        miraklIbanBankAccountInformation.setBic("BIC");
        miraklIbanBankAccountInformation.setBankZip("1111AA");
        miraklIbanBankAccountInformation.setBankStreet("1 street");
        shop.setPaymentInformation(miraklIbanBankAccountInformation);

        UpdateAccountHolderRequest request = shopService.updateAccountHolderRequestFromShop(shop);
        assertEquals("id", request.getAccountHolderCode());
        assertEquals("GB", request.getAccountHolderDetails().getBankAccountDetails().get(0).getCountryCode());
        assertEquals("Owner", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerName());
        assertEquals("IBAN", request.getAccountHolderDetails().getBankAccountDetails().get(0).getIban());
        assertEquals("BIC", request.getAccountHolderDetails().getBankAccountDetails().get(0).getBankBicSwift());
        assertEquals("1111AA", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerPostalCode());
        assertEquals("BIC", request.getAccountHolderDetails().getBankAccountDetails().get(0).getBankBicSwift());
        assertEquals("1", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerHouseNumberOrName());
    }
}
