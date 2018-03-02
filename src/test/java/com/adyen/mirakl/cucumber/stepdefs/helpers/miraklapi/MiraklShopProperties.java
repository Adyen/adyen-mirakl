package com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi;

import com.github.javafaker.Faker;
import com.google.common.collect.ImmutableList;
import com.mirakl.client.mmp.domain.shop.MiraklProfessionalInformation;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import com.mirakl.client.mmp.domain.shop.create.MiraklCreateShopAddress;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreateShop;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreateShopNewUser;
import com.mirakl.client.mmp.operator.request.shop.MiraklCreateShopsRequest;
import com.mirakl.client.mmp.request.additionalfield.MiraklRequestAdditionalFieldValue;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MiraklShopProperties {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public MiraklCreateShopsRequest createMiraklShopRequest(Map tableData, boolean createShareHolderDate) {
        Faker faker = new Faker(new Locale("en-GB"));

        String email = ("adyen-mirakl-".concat(UUID.randomUUID().toString()).concat("@mailinator.com"));
        String companyName = faker.company().name();
        String shopName = companyName.concat("-").concat(RandomStringUtils.randomAlphanumeric(8)).toLowerCase();
        String firstName = faker.name().firstName();
        String lastName = faker.name().lastName();

        MiraklCreateShop createShop = new MiraklCreateShop();

        String city;

        if (tableData.get("city") == null || StringUtils.isEmpty(tableData.get("city").toString())) {
            city = faker.address().city();
        } else {
            city = tableData.get("city").toString();
        }

        MiraklCreateShopAddress address = new MiraklCreateShopAddress();
        address.setCity(city);
        address.setCivility("Mr");
        address.setCountry("GBR");
        address.setFirstname(firstName);
        address.setLastname(lastName);
        address.setStreet1(faker.address().streetAddress());
        address.setZipCode(faker.address().zipCode());
        createShop.setAddress(address);

        createShop.setProfessional(true);
        MiraklProfessionalInformation professionalInformation = new MiraklProfessionalInformation();
        professionalInformation.setCorporateName(companyName);
        professionalInformation.setIdentificationNumber(UUID.randomUUID().toString());
        createShop.setProfessionalInformation(professionalInformation);

        MiraklCreateShopNewUser newUser = new MiraklCreateShopNewUser();
        newUser.setEmail(email);
        createShop.setNewUser(newUser);
        createShop.setEmail(email);

        log.info(String.format("\nShop name to create: [%s]", shopName));
        createShop.setName(shopName);

        if (createShareHolderDate) {
            ImmutableList.Builder<MiraklRequestAdditionalFieldValue> builder = ImmutableList.builder();
            for (int i = 1; i <= Integer.valueOf(tableData.get("maxUbos").toString()); i++) {
                builder.add(createAdditionalField("adyen-ubo"+i+"-civility", "Mr"));
                builder.add(createAdditionalField("adyen-ubo"+i+"-firstname", faker.name().firstName()));
                builder.add(createAdditionalField("adyen-ubo"+i+"-lastname", faker.name().lastName()));
                builder.add(createAdditionalField("adyen-ubo"+i+"-email", email));
            }
            builder.add(createAdditionalField("adyen-individual-housenumber", faker.address().streetAddressNumber()));
            builder.add(createAdditionalField("adyen-legal-entity-type", tableData.get("legalEntity").toString()));
            createShop.setAdditionalFieldValues(builder.build());
        } else {
            createShop.setAdditionalFieldValues(ImmutableList.of(createAdditionalField("adyen-individual-housenumber", faker.address().streetAddressNumber()),
                                                                 createAdditionalField("adyen-legal-entity-type", tableData.get("legalEntity").toString())));
        }

        String owner;
        String bankName;
        String iban;
        String bic;

        if (tableData.get("bank name") == null) {
            log.info("Bank account information will not be created in this test.");
        } else {
            owner = firstName.concat(" ").concat(lastName);
            bankName = tableData.get("bank name").toString();
            iban = faker.finance().iban();
            bic = faker.finance().bic();
            createShop.setPaymentInformation(miraklIbanBankAccountInformation(owner, bankName, iban, bic));
        }

        return new MiraklCreateShopsRequest(ImmutableList.of(createShop));
    }

    protected MiraklRequestAdditionalFieldValue.MiraklSimpleRequestAdditionalFieldValue createAdditionalField(String code, String value) {
        return new MiraklRequestAdditionalFieldValue.MiraklSimpleRequestAdditionalFieldValue(code, value);
    }

    protected MiraklIbanBankAccountInformation miraklIbanBankAccountInformation(String owner, String bankName, String iban, String bic) {

        MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = new MiraklIbanBankAccountInformation();
        miraklIbanBankAccountInformation.setOwner(owner);
        miraklIbanBankAccountInformation.setBankName(bankName);
        miraklIbanBankAccountInformation.setIban(iban);
        miraklIbanBankAccountInformation.setBic(bic);
        return miraklIbanBankAccountInformation;
    }
}
