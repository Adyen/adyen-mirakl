package com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper;

import com.adyen.model.marketpay.GetAccountHolderResponse;
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

import java.util.*;

@Service
public class AssertionHelper {

    private static final Map<String, String> CIVILITY_TO_GENDER = ImmutableMap.<String, String>builder().put("Mr", "MALE").put("Mrs", "FEMALE").put("Miss", "FEMALE").build();

    private DocumentContext parsedBankAccountDetail;

    public ImmutableList.Builder<String> adyenIndividualAccountDataBuilder(GetAccountHolderResponse getAccountHolderResponse) {
        ImmutableList.Builder<String> adyenShopData = new ImmutableList.Builder<>();
        adyenShopData.add(getAccountHolderResponse.getAccountHolderCode());
        adyenShopData.add(getAccountHolderResponse.getAccountHolderDetails().getIndividualDetails().getName().getFirstName());
        adyenShopData.add(getAccountHolderResponse.getAccountHolderDetails().getIndividualDetails().getName().getLastName());
        adyenShopData.add(getAccountHolderResponse.getAccountHolderDetails().getIndividualDetails().getName().getGender().toString());
        adyenShopData.add(getAccountHolderResponse.getAccountHolderDetails().getEmail());
        adyenShopData.add(getAccountHolderResponse.getLegalEntity().toString());
        adyenShopData.add(getAccountHolderResponse.getAccountHolderDetails().getAddress().getCity());
        adyenShopData.add(getAccountHolderResponse.getAccountHolderDetails().getAddress().getPostalCode());
        return adyenShopData;
    }

    public ImmutableList.Builder<String> adyenShareHolderAccountDataBuilder(MiraklShop shop, DocumentContext notificationResponse) {
        ImmutableList.Builder<String> adyenShopData = new ImmutableList.Builder<>();

        JSONArray uboArray = notificationResponse.read("content.accountHolderDetails.businessDetails.shareholders[*]");

        for (Object ubo : uboArray) {
            adyenShopData.add(JsonPath.parse(ubo).read("ShareholderContact.name.firstName").toString());
            adyenShopData.add(JsonPath.parse(ubo).read("ShareholderContact.name.lastName").toString());
            adyenShopData.add(JsonPath.parse(ubo).read("ShareholderContact.name.gender").toString());
            adyenShopData.add(JsonPath.parse(ubo).read("ShareholderContact.email").toString());
            Object shareholderContact = JsonPath.parse(ubo).read("ShareholderContact");
            if (shareholderContact.toString().contains("address")) {
                adyenShopData.add(JsonPath.parse(ubo).read("ShareholderContact.address.city").toString());
                adyenShopData.add(JsonPath.parse(ubo).read("ShareholderContact.address.country").toString());
                adyenShopData.add(JsonPath.parse(ubo).read("ShareholderContact.address.postalCode").toString());
                if (shop.getContactInformation().getCountry().equals("NLD")) {
                    adyenShopData.add(
                        JsonPath.parse(ubo).read("ShareholderContact.address.street").toString() + " " +
                            JsonPath.parse(ubo).read("ShareholderContact.address.houseNumberOrName").toString());
                } else {
                    adyenShopData.add(JsonPath.parse(ubo).read("ShareholderContact.address.street").toString());
                    adyenShopData.add(JsonPath.parse(ubo).read("ShareholderContact.address.houseNumberOrName").toString());
                }
            }
        }
        return adyenShopData;
    }

    public ImmutableList.Builder<String> adyenBankAccountDetail(List<Map<Object, Object>> bankAccountDetails, List<Map<String, String>> rows) {
        Map bankAccountDetail = new HashMap();
        // if more than one bankAccountDetail is returned then we need to check if the one we care about is there
        if (bankAccountDetails.size() > 1) {
            for (Map jsonArray : bankAccountDetails) {
                String iban = JsonPath.parse(jsonArray).read("BankAccountDetail.iban").toString();
                if (iban.equals(rows.get(0).get("iban"))) {
                    bankAccountDetail = (Map) jsonArray.get("BankAccountDetail");
                }
            }
        } else {
            bankAccountDetail = (Map) bankAccountDetails.get(0).get("BankAccountDetail");
        }

        ImmutableList.Builder<String> adyenShopData = new ImmutableList.Builder<>();

        parsedBankAccountDetail = JsonPath.parse(bankAccountDetail);
        adyenShopData.add(parsedBankAccountDetail.read("iban").toString());
        adyenShopData.add(parsedBankAccountDetail.read("bankBicSwift").toString());
        adyenShopData.add(parsedBankAccountDetail.read("ownerName").toString());

        return adyenShopData;
    }

    public ImmutableList.Builder<String> miraklShopShareHolderDataBuilder(MiraklShop miraklShop, List<Map<String, String>> rows) {
        ImmutableList.Builder<String> miraklShopData = new ImmutableList.Builder<>();
        List<String> shopAdditionalFields = new ArrayList<>();
        int maxUbos = Integer.parseInt(rows.get(0).get("maxUbos"));
        for (int i = 1; i <= maxUbos; i++) {
            shopAdditionalFields.add("adyen-ubo" + i + "-firstname");
            shopAdditionalFields.add("adyen-ubo" + i + "-lastname");
            shopAdditionalFields.add("adyen-ubo" + i + "-civility");
            shopAdditionalFields.add("adyen-ubo" + i + "-email");
            shopAdditionalFields.add("adyen-ubo" + i + "-city");
            shopAdditionalFields.add("adyen-ubo" + i + "-country");
            if (miraklShop.getContactInformation().getCountry().equals("NLD")) {
                shopAdditionalFields.add("adyen-ubo" + i + "-streetname");
            } else {
                shopAdditionalFields.add("adyen-ubo" + i + "-housenumber");
                shopAdditionalFields.add("adyen-ubo" + i + "-streetname");
            }
            shopAdditionalFields.add("adyen-ubo" + i + "-zip");
        }
        for (String field : shopAdditionalFields) {
            String fieldValue = miraklShop.getAdditionalFieldValues()
                .stream()
                .filter(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue.class::isInstance)
                .map(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue.class::cast)
                .filter(x -> field.equals(x.getCode()))
                .map(MiraklAdditionalFieldValue.MiraklAbstractAdditionalFieldWithSingleValue::getValue)
                .findAny()
                .orElse("");
            if (field.contains("civility")) {
                miraklShopData.add(CIVILITY_TO_GENDER.get(fieldValue));
            } else {
                if (!fieldValue.isEmpty()){
                    miraklShopData.add(fieldValue);
                }
            }
        }

        return miraklShopData;
    }

    public ImmutableList.Builder<String> miraklBankAccountInformation(MiraklShop miraklShop) {
        ImmutableList.Builder<String> miraklShopData = new ImmutableList.Builder<>();

        MiraklPaymentInformation paymentInformation = miraklShop.getPaymentInformation();
        if (paymentInformation instanceof MiraklIbanBankAccountInformation) {
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
        miraklShopData.add(miraklShop.getContactInformation().getCity());
        miraklShopData.add(miraklShop.getContactInformation().getZipCode());
        return miraklShopData;
    }

    public DocumentContext getParsedBankAccountDetail() {
        return parsedBankAccountDetail;
    }
}
