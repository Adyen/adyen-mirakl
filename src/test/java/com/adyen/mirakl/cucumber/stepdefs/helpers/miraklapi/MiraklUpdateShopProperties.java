package com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi;

import com.adyen.mirakl.service.UboService;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.mirakl.client.domain.common.error.ErrorBean;
import com.mirakl.client.domain.common.error.InputWithErrors;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.shop.MiraklProfessionalInformation;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShopAddress;
import com.mirakl.client.mmp.domain.shop.MiraklShopState;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import com.mirakl.client.mmp.domain.shop.bank.MiraklPaymentInformation;
import com.mirakl.client.mmp.operator.domain.shop.update.MiraklUpdateShop;
import com.mirakl.client.mmp.operator.domain.shop.update.MiraklUpdatedShopReturn;
import com.mirakl.client.mmp.operator.domain.shop.update.MiraklUpdatedShops;
import com.mirakl.client.mmp.request.additionalfield.MiraklRequestAdditionalFieldValue;
import com.mirakl.client.mmp.request.additionalfield.MiraklRequestAdditionalFieldValue.MiraklSimpleRequestAdditionalFieldValue;
import com.mirakl.client.mmp.request.common.document.MiraklUploadDocument;
import com.mirakl.client.mmp.request.shop.document.MiraklUploadShopDocumentsRequest;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;

import javax.annotation.Resource;
import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

class MiraklUpdateShopProperties extends AbstractMiraklShopSharedProperties {

    @Resource
    private UboService uboService;

    ImmutableList.Builder<MiraklSimpleRequestAdditionalFieldValue> addMiraklShopUbos(List<Map<String, String>> rows) {

        ImmutableList.Builder<MiraklSimpleRequestAdditionalFieldValue> builder = ImmutableList.builder();
        rows.forEach(row -> {
            maxUbos = row.get("maxUbos");
            Map<Integer, Map<String, String>> uboKeys = uboService.generateMiraklUboKeys(4);
            if (maxUbos != null) {
                /* adding more ubos to a shop is dictated by the number of UBOs to update (as defined in Cucumber table)
                 example:- 3 UBOs to add. (4 - 3) + 1 = 2
                 so the method will start at UBO 2 until 4*/
                int noOfUbos = Integer.valueOf(maxUbos);
                for (noOfUbos = (4 - noOfUbos) + 1; noOfUbos <= 4; noOfUbos++) {
                    builder.add(createAdditionalField(uboKeys.get(noOfUbos).get(UboService.CIVILITY), civility()));
                    builder.add(createAdditionalField(uboKeys.get(noOfUbos).get(UboService.FIRSTNAME), FAKER.name().firstName()));
                    builder.add(createAdditionalField(uboKeys.get(noOfUbos).get(UboService.LASTNAME), FAKER.name().lastName()));
                    builder.add(createAdditionalField(uboKeys.get(noOfUbos).get(UboService.EMAIL), email));
                }
            }
        });
        return builder;
    }

    ImmutableList.Builder<MiraklSimpleRequestAdditionalFieldValue> updateMiraklShopUbos(List<Map<String, String>> rows) {
        ImmutableList.Builder<MiraklSimpleRequestAdditionalFieldValue> builder = ImmutableList.builder();
        rows.forEach(row -> {
            maxUbos = row.get("UBO");
            int ubo = Integer.valueOf(maxUbos);
            Map<Integer, Map<String, String>> uboKeys = uboService.generateMiraklUboKeys(Integer.valueOf(maxUbos));
            builder.add(createAdditionalField(uboKeys.get(ubo).get(UboService.FIRSTNAME), row.get("firstName")));
            builder.add(createAdditionalField(uboKeys.get(ubo).get(UboService.LASTNAME), row.get("lastName")));
            builder.add(createAdditionalField(uboKeys.get(ubo).get(UboService.EMAIL), "adyen-mirakl" + UUID.randomUUID() + "@mailtrap.com"));

        });
        return builder;
    }

    ImmutableList.Builder<MiraklSimpleRequestAdditionalFieldValue> updateMiraklShopUbosWithInvalidData(List<Map<String, String>> rows) {
        ImmutableList.Builder<MiraklSimpleRequestAdditionalFieldValue> builder = ImmutableList.builder();
        rows.forEach(row -> {
            maxUbos = row.get("UBO");
            int ubo = Integer.valueOf(maxUbos);
            Map<Integer, Map<String, String>> uboKeys = uboService.generateMiraklUboKeys(Integer.valueOf(maxUbos));
            builder.add(createAdditionalField(uboKeys.get(ubo).get(UboService.FIRSTNAME),UUID.randomUUID().toString() ));
            builder.add(createAdditionalField(uboKeys.get(ubo).get(UboService.LASTNAME), UUID.randomUUID().toString()));
        });
        return builder;
    }

    MiraklProfessionalInformation updateMiraklShopTaxId(MiraklShop miraklShop) {
        MiraklProfessionalInformation miraklProfessionalInformation = new MiraklProfessionalInformation();
        miraklProfessionalInformation.setTaxIdentificationNumber("GB" + RandomStringUtils.randomNumeric(9));
        miraklProfessionalInformation.setIdentificationNumber(miraklShop.getProfessionalInformation().getIdentificationNumber());
        miraklProfessionalInformation.setCorporateName(miraklShop.getProfessionalInformation().getCorporateName());
        return miraklProfessionalInformation;
    }

    MiraklUploadShopDocumentsRequest uploadMiraklShopWithBankStatement(String shopId) {
        ImmutableList.Builder<MiraklUploadDocument> docUploadRequestBuilder = new ImmutableList.Builder<>();

        URL url = Resources.getResource("fileuploads/BankStatement.jpg");

        MiraklUploadDocument element = new MiraklUploadDocument();
        element.setFile(new File(url.getPath()));
        element.setFileName("BankStatement.jpg");
        element.setTypeCode("adyen-bankproof");

        docUploadRequestBuilder.add(element);

        return miraklUploadShopDocumentsRequest(shopId, docUploadRequestBuilder.build());
    }

    MiraklIbanBankAccountInformation updateNewMiraklIbanOnly(MiraklShop miraklShop, List<Map<String, String>> rows) {
        MiraklIbanBankAccountInformation paymentInformation = new MiraklIbanBankAccountInformation();
        MiraklPaymentInformation miraklPaymentInformation = miraklShop.getPaymentInformation();
        if (miraklPaymentInformation instanceof MiraklIbanBankAccountInformation) {
            paymentInformation.setIban(rows.get(0).get("iban"));
            paymentInformation.setBic(((MiraklIbanBankAccountInformation) miraklPaymentInformation).getBic());
            paymentInformation.setOwner(miraklPaymentInformation.getOwner());
            paymentInformation.setBankName(((MiraklIbanBankAccountInformation) miraklPaymentInformation).getBankName());
        }
        return paymentInformation;
    }

    MiraklShopAddress updateMiraklShopAddress(MiraklShop miraklShop, Map<String, String> row) {
        MiraklShopAddress address = new MiraklShopAddress();

        address.setCity(row.get("city"));
        address.setCivility(miraklShop.getContactInformation().getCivility());
        address.setCountry(miraklShop.getContactInformation().getCountry());
        address.setFirstname(row.get("firstName"));
        address.setLastname(row.get("lastName"));
        address.setStreet1(miraklShop.getContactInformation().getStreet1());
        address.setZipCode(row.get("postCode"));

        return address;
    }

    // Mandatory for shop update
    void populateMiraklShopPremiumSuspendAndPaymentBlockedStatus(MiraklShop miraklShop,
                                                                 MiraklUpdateShop miraklUpdateShop) {

        // will keep setSuspend false unless returned enum = SUSPEND
        boolean setSuspend;
        MiraklShopState state = miraklShop.getState();
        setSuspend = state.equals(MiraklShopState.SUSPENDED);
        miraklUpdateShop.setSuspend(setSuspend);
        miraklUpdateShop.setProfessional(miraklShop.isProfessional());
        miraklUpdateShop.setPaymentBlocked(miraklShop.getPaymentDetail().getPaymentBlocked());
        miraklUpdateShop.setPremiumState(miraklShop.getPremiumState());
    }

    void populateMiraklChannel(MiraklShop miraklShop, MiraklUpdateShop miraklUpdateShop) {
        // gets the sales channel code, if multiple found then immutable list should handle
        ImmutableList.Builder<String> channelsBuilder = new ImmutableList.Builder<>();
        for (String channel : miraklShop.getChannels()) {
            channelsBuilder.add(channel);
        }
        miraklUpdateShop.setChannels(channelsBuilder.build());
    }

    // Mandatory for shop update
    void populateShopNameAndEmail(MiraklShop miraklShop, MiraklUpdateShop miraklUpdateShop) {
        miraklUpdateShop.setName(miraklShop.getName());
        miraklUpdateShop.setEmail(miraklShop.getContactInformation().getEmail());
    }

    // Mandatory for shop update
    MiraklShopAddress populateMiraklShopAddress(MiraklShop miraklShop) {
        MiraklShopAddress address = new MiraklShopAddress();

        address.setCity(miraklShop.getContactInformation().getCity());
        address.setCivility(miraklShop.getContactInformation().getCivility());
        address.setCountry(miraklShop.getContactInformation().getCountry());
        address.setFirstname(miraklShop.getContactInformation().getFirstname());
        address.setLastname(miraklShop.getContactInformation().getLastname());
        address.setStreet1(miraklShop.getContactInformation().getStreet1());
        address.setZipCode(miraklShop.getContactInformation().getZipCode());

        return address;
    }

    // Mandatory for shop update
    MiraklIbanBankAccountInformation populateMiraklIbanBankAccountInformation(MiraklShop miraklShop) {
        MiraklIbanBankAccountInformation paymentInformation = new MiraklIbanBankAccountInformation();

        //update requires bank details for some reason
        if (miraklShop.getPaymentInformation() != null) {
            if (miraklShop.getPaymentInformation() instanceof MiraklIbanBankAccountInformation) {
                MiraklIbanBankAccountInformation ibanBankAccountInformation = (MiraklIbanBankAccountInformation) miraklShop.getPaymentInformation();
                paymentInformation.setIban(ibanBankAccountInformation.getIban());
                paymentInformation.setBic(ibanBankAccountInformation.getBic());
                paymentInformation.setOwner(ibanBankAccountInformation.getOwner());
                paymentInformation.setBankName(ibanBankAccountInformation.getBankName());
                paymentInformation.setBankCity(ibanBankAccountInformation.getBankCity());
            }
        } else {
            paymentInformation.setIban("GB26TEST40051512347366");
            paymentInformation.setBic(FAKER.finance().bic());
            paymentInformation.setOwner(FAKER.name().firstName() + " " + FAKER.name().lastName());
            paymentInformation.setBankName("RBS");
            paymentInformation.setBankCity("PASSED");
        }
        return paymentInformation;
    }

    void populateMiraklAdditionalFields(MiraklUpdateShop miraklUpdateShop, MiraklShop miraklShop,
                                        ImmutableList<MiraklSimpleRequestAdditionalFieldValue> fieldsToUpdate) {

        final List<MiraklAdditionalFieldValue> addFields = new LinkedList<>(miraklShop.getAdditionalFieldValues());
        final ImmutableList.Builder<MiraklRequestAdditionalFieldValue> updatedFields = new ImmutableList.Builder<>();

        for (MiraklSimpleRequestAdditionalFieldValue additionalFieldVal : fieldsToUpdate) {
            Optional<MiraklAdditionalFieldValue.MiraklStringAdditionalFieldValue> additionalField = addFields.stream()
                .filter(x -> additionalFieldVal.getCode().equals(x.getCode()))
                .filter(MiraklAdditionalFieldValue.MiraklStringAdditionalFieldValue.class::isInstance)
                .map(MiraklAdditionalFieldValue.MiraklStringAdditionalFieldValue.class::cast).findAny();

            // if fields are present then update them
            // else create them
            if (additionalField.isPresent()) {
                MiraklAdditionalFieldValue.MiraklStringAdditionalFieldValue field = additionalField.get();
                field.setValue(additionalFieldVal.getValue());
            } else {
                updatedFields.add(additionalFieldVal);
            }
        }

        // update patch

        final List<MiraklRequestAdditionalFieldValue> patchedUpdatedFields = new LinkedList<>();
        for (MiraklAdditionalFieldValue field : addFields) {
            if (field instanceof MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithMultipleValues) {
                patchedUpdatedFields.add(new MiraklRequestAdditionalFieldValue.MiraklMultipleRequestAdditionalFieldValue(field.getCode(),
                    ((MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithMultipleValues) field).getValues()));
            } else if (field instanceof MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue) {
                patchedUpdatedFields.add(new MiraklSimpleRequestAdditionalFieldValue(field.getCode(),
                    ((MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue) field).getValue()));
            } else {
                Assertions.fail("unexpected additional field type {0} ", field.getClass());
            }
        }
        updatedFields.addAll(patchedUpdatedFields);
        miraklUpdateShop.setAdditionalFieldValues(updatedFields.build());
    }

    void throwErrorIfShopFailedToUpdate(MiraklUpdatedShops miraklUpdatedShopsResponse) {
        final List<Set<ErrorBean>> errors = miraklUpdatedShopsResponse.getShopReturns().stream()
            .map(MiraklUpdatedShopReturn::getShopError)
            .filter(Objects::nonNull)
            .map(InputWithErrors::getErrors)
            .collect(Collectors.toList());

        Assertions.assertThat(errors.size()).withFailMessage("errors on update: " + GSON.toJson(errors)).isZero();
    }
}
