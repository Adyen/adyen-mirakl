package com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi;

import com.adyen.mirakl.service.UboService;
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
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class MiraklShopProperties extends AbstractMiraklShopSharedProperties{

    @Resource
    private UboService uboService;

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private String companyName = FAKER.company().name();
    private String firstName = FAKER.name().firstName();

    void populatePaymentInformation(List<Map<String, String>> rows, MiraklCreateShop createShop) {

        rows.forEach(row -> {
            String owner;
            String bankName;
            String iban;
            String bic;
            String city;

            if (row.get("bank name") == null) {
                log.info("Bank account information will not be created in this test.");
            } else {
                owner = row.get("bankOwnerName");
                bankName = row.get("bank name");
                iban = row.get("iban");
                bic = FAKER.finance().bic();
                city = row.get("city");
                createShop.setPaymentInformation(miraklIbanBankAccountInformation(owner, bankName, iban, bic, city));
            }
        });
    }

    void populateShareHolderData(String legalEntity, List<Map<String, String>> rows, MiraklCreateShop createShop) {
        rows.forEach(row -> {
            maxUbos = row.get("maxUbos");
            if (maxUbos != null) {
                ImmutableList.Builder<MiraklRequestAdditionalFieldValue> builder = ImmutableList.builder();
                for (int i = 1; i <= Integer.valueOf(maxUbos); i++) {

                    Map<Integer, Map<String, String>> uboKeys = uboService.generateMiraklUboKeys(Integer.valueOf(maxUbos));
                    buildShareHolderMinimumData(builder, i, uboKeys, civility());
                    builder.add(createAdditionalField(uboKeys.get(i).get(UboService.COUNTRY), "GB"));
                    builder.add(createAdditionalField(uboKeys.get(i).get(UboService.HOUSE_NUMBER_OR_NAME), FAKER.address().streetAddressNumber()));
                    builder.add(createAdditionalField(uboKeys.get(i).get(UboService.STREET), FAKER.address().streetName()));
                    builder.add(createAdditionalField(uboKeys.get(i).get(UboService.CITY), FAKER.address().city()));
                    builder.add(createAdditionalField(uboKeys.get(i).get(UboService.POSTAL_CODE), FAKER.address().zipCode()));
                    builder.add(createAdditionalField(uboKeys.get(i).get(UboService.PHONE_COUNTRY_CODE), "GB"));
                    builder.add(createAdditionalField(uboKeys.get(i).get(UboService.PHONE_NUMBER), FAKER.phoneNumber().phoneNumber()));
                    builder.add(createAdditionalField(uboKeys.get(i).get(UboService.DATE_OF_BIRTH), dateOfBirth().toString()));
                    builder.add(createAdditionalField(uboKeys.get(i).get(UboService.NATIONALITY), "GB"));
                    builder.add(createAdditionalField(uboKeys.get(i).get(UboService.ID_NUMBER), UUID.randomUUID().toString()));
                }
                builder.add(createAdditionalField("adyen-legal-entity-type", legalEntity));
                createShop.setAdditionalFieldValues(builder.build());
            }
        });
    }

    private DateTime dateOfBirth() {
        String dob = "1989-03-15 23:00:00";
        org.joda.time.format.DateTimeFormatter formatter = DateTimeFormat.forPattern("yyy-MM-dd HH:mm:ss");
        return formatter.parseDateTime(dob);
    }

    void populateShareholderWithMissingData(String legalEntity, List<Map<String, String>> rows, MiraklCreateShop createShop) {
        rows.forEach(row -> {
            maxUbos = row.get(maxUbos);
            if (maxUbos != null) {
                ImmutableList.Builder<MiraklRequestAdditionalFieldValue> builder = ImmutableList.builder();
                Map<Integer, Map<String, String>> uboKeys = uboService.generateMiraklUboKeys(Integer.valueOf(maxUbos));

                for (int i = 1; i <= Integer.valueOf(maxUbos); i++) {

                    buildShareHolderMinimumData(builder, i, uboKeys, civility());
                }
                builder.add(createAdditionalField("adyen-legal-entity-type", legalEntity));
                createShop.setAdditionalFieldValues(builder.build());
            }
        });
    }

    void populateAddFieldsLegalAndHouseNumber(String legalEntity, MiraklCreateShop createShop) {

        createShop.setAdditionalFieldValues(ImmutableList.of(
            createAdditionalField("adyen-individual-housenumber", FAKER.address().streetAddressNumber()),
            createAdditionalField("adyen-legal-entity-type", legalEntity),
            createAdditionalField("adyen-individual-dob", dateOfBirth().toString()),
            createAdditionalField("adyen-individual-idnumber", "01234567890")
        ));
    }

    void populateUserEmailAndShopName(MiraklCreateShop createShop, List<Map<String, String>> rows) {

        String shopName;
        if (rows.get(0).get("companyName") == null) {
            shopName = companyName.concat("-").concat(RandomStringUtils.randomAlphanumeric(8)).toLowerCase();
        } else {
            shopName = rows.get(0).get("companyName");
        }

        MiraklCreateShopNewUser newUser = new MiraklCreateShopNewUser();
        String email = "adyen-mirakl-".concat(UUID.randomUUID().toString()).concat("@mailtrap.com");
        newUser.setEmail(email);
        createShop.setNewUser(newUser);
        createShop.setEmail(email);

        log.info(String.format("\nShop name to create: [%s]", shopName));
        createShop.setName(shopName);
    }

    void populateMiraklProfessionalInformation(MiraklCreateShop createShop) {
        createShop.setProfessional(true);
        MiraklProfessionalInformation professionalInformation = new MiraklProfessionalInformation();
        professionalInformation.setCorporateName(companyName);
        professionalInformation.setIdentificationNumber(UUID.randomUUID().toString());
        createShop.setProfessionalInformation(professionalInformation);
    }

    void populateMiraklAddress(List<Map<String, String>> rows, MiraklCreateShop createShop) {
        rows.forEach(row -> {
            String city;

            if (row.get("city") == null || StringUtils.isEmpty(row.get("city"))) {
                city = FAKER.address().city();
            } else {
                city = row.get("city");
            }

            MiraklCreateShopAddress address = new MiraklCreateShopAddress();
            address.setCity(city);
            address.setCivility("Mr");
            address.setCountry("GBR");
            address.setFirstname(firstName);
            address.setLastname(row.get("lastName"));
            address.setStreet1(FAKER.address().streetAddress());
            address.setZipCode(FAKER.address().zipCode());
            createShop.setAddress(address);
        });
    }

    void throwErrorIfShopIsNotCreated(MiraklCreatedShops shops) {
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

    private MiraklIbanBankAccountInformation miraklIbanBankAccountInformation(String owner, String bankName, String iban, String bic, String city) {

        MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = new MiraklIbanBankAccountInformation();
        miraklIbanBankAccountInformation.setOwner(owner);
        miraklIbanBankAccountInformation.setBankName(bankName);
        miraklIbanBankAccountInformation.setIban(iban);
        miraklIbanBankAccountInformation.setBic(bic);
        miraklIbanBankAccountInformation.setBankCity(city);
        return miraklIbanBankAccountInformation;
    }
}
