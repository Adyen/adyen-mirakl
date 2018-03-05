package com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi;

import com.github.javafaker.Faker;
import com.google.common.collect.ImmutableList;
import com.mirakl.client.mmp.domain.shop.MiraklProfessionalInformation;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import com.mirakl.client.mmp.domain.shop.create.MiraklCreateShopAddress;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreateShop;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreateShopNewUser;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShopReturn;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import com.mirakl.client.mmp.request.additionalfield.MiraklRequestAdditionalFieldValue;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MiraklShopProperties extends AbstractMiraklShopSharedProperties{

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    Faker faker = new Faker(new Locale("en-GB"));

    private String email = "adyen-mirakl-".concat(UUID.randomUUID().toString()).concat("@mailinator.com");
    private String companyName = faker.company().name();
    private String firstName = faker.name().firstName();

    protected void populatePaymentInformation(List<Map<Object, Object>> rows, MiraklCreateShop createShop) {

        rows.forEach(row -> {
            String owner;
            String bankName;
            String iban;
            String bic;

            if (row.get("bank name") == null) {
                log.info("Bank account information will not be created in this test.");
            } else {
                owner = row.get("bankOwnerName").toString();
                bankName = row.get("bank name").toString();
                iban = row.get("iban").toString();
                bic = faker.finance().bic();
                createShop.setPaymentInformation(miraklIbanBankAccountInformation(owner, bankName, iban, bic));
            }
        });
    }

    protected void populateShareHolderData(String legalEntity, List<Map<Object, Object>> rows, MiraklCreateShop createShop) {

        rows.forEach(row -> {
            if (row.get("maxUbos") != null) {
                ImmutableList.Builder<MiraklRequestAdditionalFieldValue> builder = ImmutableList.builder();
                for (int i = 1; i <= Integer.valueOf(row.get("maxUbos").toString()); i++) {
                    builder.add(createAdditionalField("adyen-ubo" + i + "-civility", "Mr"));
                    builder.add(createAdditionalField("adyen-ubo" + i + "-firstname", faker.name().firstName()));
                    builder.add(createAdditionalField("adyen-ubo" + i + "-lastname", faker.name().lastName()));
                    builder.add(createAdditionalField("adyen-ubo" + i + "-email", email));
                }
                builder.add(createAdditionalField("adyen-individual-housenumber", faker.address().streetAddressNumber()));
                builder.add(createAdditionalField("adyen-legal-entity-type", legalEntity));
                createShop.setAdditionalFieldValues(builder.build());
            }
        });
    }

    protected void populateAddFieldsLegalAndHouseNumber(String legalEntity, MiraklCreateShop createShop) {
        createShop.setAdditionalFieldValues(ImmutableList.of(createAdditionalField("adyen-individual-housenumber", faker.address().streetAddressNumber()),
            createAdditionalField("adyen-legal-entity-type", legalEntity)));
    }

    protected void populateUserEmailAndShopName(MiraklCreateShop createShop) {
        String shopName = companyName.concat("-").concat(RandomStringUtils.randomAlphanumeric(8)).toLowerCase();
        MiraklCreateShopNewUser newUser = new MiraklCreateShopNewUser();
        String email = "adyen-mirakl-".concat(UUID.randomUUID().toString()).concat("@mailinator.com");
        newUser.setEmail(email);
        createShop.setNewUser(newUser);
        createShop.setEmail(email);

        log.info(String.format("\nShop name to create: [%s]", shopName));
        createShop.setName(shopName);
    }

    protected void populateMiraklProfessionalInformation(MiraklCreateShop createShop) {
        createShop.setProfessional(true);
        MiraklProfessionalInformation professionalInformation = new MiraklProfessionalInformation();
        professionalInformation.setCorporateName(companyName);
        professionalInformation.setIdentificationNumber(UUID.randomUUID().toString());
        createShop.setProfessionalInformation(professionalInformation);
    }

    protected void populateMiraklAddress(List<Map<Object, Object>> rows, MiraklCreateShop createShop) {
        rows.forEach(row -> {
            String city;

            if (row.get("city") == null || StringUtils.isEmpty(row.get("city").toString())) {
                city = faker.address().city();
            } else {
                city = row.get("city").toString();
            }

            MiraklCreateShopAddress address = new MiraklCreateShopAddress();
            address.setCity(city);
            address.setCivility("Mr");
            address.setCountry("GBR");
            address.setFirstname(firstName);
            address.setLastname(row.get("lastName").toString());
            address.setStreet1(faker.address().streetAddress());
            address.setZipCode(faker.address().zipCode());
            createShop.setAddress(address);
        });
    }

    protected void throwErrorIfShopIsNotCreated(MiraklCreatedShops shops) {
        MiraklCreatedShopReturn miraklCreatedShopReturn = shops.getShopReturns()
            .stream()
            .findAny()
            .orElseThrow(() -> new IllegalStateException("No Shop found"));

        if (miraklCreatedShopReturn.getShopCreated() == null) {
            throw new IllegalStateException(miraklCreatedShopReturn.getShopError().getErrors().toString());
        }
        String shopId = shops.getShopReturns().iterator().next().getShopCreated().getId();
        log.info(String.format("Mirakl Shop Id: [%s]", shopId));
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
