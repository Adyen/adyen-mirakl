package com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import com.mirakl.client.mmp.domain.shop.bank.MiraklPaymentInformation;
import net.minidev.json.JSONArray;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AssertionHelper {

    private static final Map<String, String> CIVILITY_TO_GENDER = ImmutableMap.<String, String>builder().put("Mr", "MALE")
        .put("Mrs", "FEMALE")
        .put("Miss", "FEMALE").build();

    public ImmutableList.Builder<String> adyenAccountDataBuilder(Map<String, Object> mappedAdyenNotificationResponse) {
        ImmutableList.Builder<String> adyenShopData = new ImmutableList.Builder<>();

        adyenShopData.add(JsonPath.parse(mappedAdyenNotificationResponse.get("content")).read("['accountHolderCode']").toString());
        adyenShopData.add(JsonPath.parse(mappedAdyenNotificationResponse.get("content")).read("['accountHolderDetails']['individualDetails']['name']['firstName']").toString());
        adyenShopData.add(JsonPath.parse(mappedAdyenNotificationResponse.get("content")).read("['accountHolderDetails']['individualDetails']['name']['lastName']").toString());
        adyenShopData.add(JsonPath.parse(mappedAdyenNotificationResponse.get("content")).read("['accountHolderDetails']['individualDetails']['name']['gender']").toString());
        adyenShopData.add(JsonPath.parse(mappedAdyenNotificationResponse.get("content")).read("['accountHolderDetails']['email']").toString());
        adyenShopData.add(JsonPath.parse(mappedAdyenNotificationResponse.get("content")).read("['legalEntity']").toString());
        return adyenShopData;
    }

    public ImmutableList.Builder<String> adyenShareHolderAccountDataBuilder(Map<String, Object> mappedAdyenNotificationResponse) {
        ImmutableList.Builder<String> adyenShopData = new ImmutableList.Builder<>();

        JSONArray uboArray = JsonPath.parse(mappedAdyenNotificationResponse.get("content")).read("accountHolderDetails.businessDetails.shareholders[*]");

        for (Object ubo : uboArray) {
            adyenShopData.add(JsonPath.parse(ubo).read("ShareholderContact.name.firstName").toString());
            adyenShopData.add(JsonPath.parse(ubo).read("ShareholderContact.name.lastName").toString());
            adyenShopData.add(JsonPath.parse(ubo).read("ShareholderContact.name.gender").toString());
            adyenShopData.add(JsonPath.parse(ubo).read("ShareholderContact.email").toString());

        }
        return adyenShopData;
    }

    public ImmutableList.Builder<String> adyenBankAccountDetail(Map adyenNotificationContent) {
        ImmutableList.Builder<String> adyenShopData = new ImmutableList.Builder<>();

        DocumentContext parse = JsonPath.parse(adyenNotificationContent);

        adyenShopData.add(parse.read("iban").toString());
        adyenShopData.add(parse.read("bankBicSwift").toString());
        adyenShopData.add(parse.read("ownerName").toString());

        return adyenShopData;
    }


    public ImmutableList.Builder<String> miraklShopShareHolderDataBuilder(MiraklShop miraklShop) {
        ImmutableList.Builder<String> miraklShopData = new ImmutableList.Builder<>();

        List<String> shopAdditionalFields = new ArrayList<>();

        for (int i = 1; i < 4; i++) {
            shopAdditionalFields.add("adyen-ubo" + i + "-firstname");
            shopAdditionalFields.add("adyen-ubo" + i + "-lastname");
            shopAdditionalFields.add("adyen-ubo" + i + "-civility");
            shopAdditionalFields.add("adyen-ubo" + i + "-email");

            for (String field : shopAdditionalFields) {
                String fieldValue = miraklShop.getAdditionalFieldValues().stream()
                    .filter(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue.class::isInstance)
                    .map(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue.class::cast)
                    .filter(x -> field.equals(x.getCode()))
                    .map(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue::getValue)
                    .findAny()
                    .orElse("");
                if (field.contains("civility")) {
                    miraklShopData.add(CIVILITY_TO_GENDER.get(fieldValue));
                } else {
                    miraklShopData.add(fieldValue);
                }
            }
        }
        return miraklShopData;
    }

    public ImmutableList.Builder<String> miraklBankAccountInformation(MiraklShop miraklShop) {
        ImmutableList.Builder<String> miraklShopData = new ImmutableList.Builder<>();

        MiraklPaymentInformation paymentInformation = miraklShop.getPaymentInformation();
        if(paymentInformation instanceof MiraklIbanBankAccountInformation){
            miraklShopData.add(((MiraklIbanBankAccountInformation) paymentInformation).getIban());
            miraklShopData.add(((MiraklIbanBankAccountInformation) paymentInformation).getBic());
        }
        miraklShopData.add(miraklShop.getPaymentInformation().getOwner());
        return miraklShopData;
    }

    public ImmutableList.Builder<String> miraklShopDataBuilder(String email, MiraklShop miraklShop) {
        ImmutableList.Builder<String> miraklShopData = new ImmutableList.Builder<>();

        miraklShopData.add(miraklShop.getId());
        miraklShopData.add(miraklShop.getContactInformation().getFirstname());
        miraklShopData.add(miraklShop.getContactInformation().getLastname());
        miraklShopData.add(CIVILITY_TO_GENDER.get(miraklShop.getContactInformation().getCivility()));
        miraklShopData.add(email);
        String element = miraklShop.getAdditionalFieldValues()
            .stream()
            .filter(MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue.class::isInstance)
            .map(MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue.class::cast)
            .filter(x -> "adyen-legal-entity-type".equals(x.getCode()))
            .map(MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue::getValue)
            .findAny()
            .orElse("");
        miraklShopData.add(element);
        return miraklShopData;
    }
}
