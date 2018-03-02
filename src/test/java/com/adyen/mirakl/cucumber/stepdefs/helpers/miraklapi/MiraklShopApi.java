package com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi;

import com.github.javafaker.Faker;
import com.google.common.collect.ImmutableList;
import com.mirakl.client.mmp.domain.shop.MiraklPremiumState;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShopAddress;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShopReturn;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import com.mirakl.client.mmp.operator.domain.shop.update.MiraklUpdateShop;
import com.mirakl.client.mmp.operator.domain.shop.update.MiraklUpdatedShops;
import com.mirakl.client.mmp.operator.request.shop.MiraklCreateShopsRequest;
import com.mirakl.client.mmp.operator.request.shop.MiraklUpdateShopsRequest;
import com.mirakl.client.mmp.request.additionalfield.MiraklRequestAdditionalFieldValue;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class MiraklShopApi extends MiraklShopProperties {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public MiraklCreatedShops createNewShop(MiraklMarketplacePlatformOperatorApiClient client, Map tableData, boolean createShopHolderData) {

        MiraklCreateShopsRequest miraklShopRequest = createMiraklShopRequest(tableData, createShopHolderData);
        MiraklCreatedShops shops = client.createShops(miraklShopRequest);

        MiraklCreatedShopReturn miraklCreatedShopReturn = shops.getShopReturns()
                                                               .stream()
                                                               .findAny()
                                                               .orElseThrow(() -> new IllegalStateException("No Shop found"));

        if (miraklCreatedShopReturn.getShopCreated() == null) {
            throw new IllegalStateException(miraklCreatedShopReturn.getShopError().getErrors().toString());
        }
        String shopId = shops.getShopReturns().iterator().next().getShopCreated().getId();
        log.info(String.format("Mirakl Shop Id: [%s]", shopId));

        return shops;
    }

    private MiraklShops getAllMiraklShops(MiraklMarketplacePlatformOperatorApiClient client) {
        MiraklGetShopsRequest request = new MiraklGetShopsRequest();
        request.setPaginate(false);
        return client.getShops(request);
    }

    public MiraklShop filterMiraklShopsByEmailAndReturnShop(MiraklMarketplacePlatformOperatorApiClient client, String email) {
        MiraklShops shops = getAllMiraklShops(client);
        return shops.getShops()
            .stream().filter(shop -> shop.getContactInformation().getEmail().equalsIgnoreCase(email)).findAny()
            .orElseThrow(() -> new IllegalStateException("Shop cannot be found."));
    }

    public MiraklUpdatedShops updateExistingShop(MiraklCreatedShops createdShops, String shopId, MiraklMarketplacePlatformOperatorApiClient client, boolean changeIbanOnly) {
        Faker faker = new Faker();
        MiraklUpdateShop element = new MiraklUpdateShop();
        element.setShopId(Long.valueOf(shopId));

        MiraklIbanBankAccountInformation paymentInformation = new MiraklIbanBankAccountInformation();
        if (changeIbanOnly) {
            paymentInformation.setIban(faker.finance().iban());

        } else {
            paymentInformation.setIban(faker.finance().iban());
            paymentInformation.setBic(faker.finance().bic());
            paymentInformation.setOwner(faker.name().firstName() + " " + faker.name().lastName());
            paymentInformation.setBankName("RBS");
        }
        element.setPaymentInformation(paymentInformation);

        MiraklShopAddress address = new MiraklShopAddress();

        for (MiraklCreatedShopReturn miraklCreatedShopReturn : createdShops.getShopReturns()) {

            element.setName(miraklCreatedShopReturn.getShopCreated().getName());
            address.setCity(miraklCreatedShopReturn.getShopCreated().getContactInformation().getCity());
            address.setCivility(miraklCreatedShopReturn.getShopCreated().getContactInformation().getCivility());
            address.setCountry(miraklCreatedShopReturn.getShopCreated().getContactInformation().getCountry());
            address.setFirstname(miraklCreatedShopReturn.getShopCreated().getContactInformation().getFirstname());
            address.setLastname(miraklCreatedShopReturn.getShopCreated().getContactInformation().getLastname());
            address.setStreet1(miraklCreatedShopReturn.getShopCreated().getContactInformation().getStreet1());
            address.setZipCode(miraklCreatedShopReturn.getShopCreated().getContactInformation().getZipCode());
            element.setAddress(address);
            element.setEmail(miraklCreatedShopReturn.getShopCreated().getContactInformation().getEmail());
        }

        element.setSuspend(false);
        element.setPaymentBlocked(false);
        element.setPremiumState(MiraklPremiumState.NOT_PREMIUM);

        element.setChannels(ImmutableList.of("INIT"));
        element.setAdditionalFieldValues(ImmutableList.of(createAdditionalField("adyen-legal-entity-type", "Individual")));

        MiraklUpdateShopsRequest request = new MiraklUpdateShopsRequest(ImmutableList.of(element));
        return client.updateShops(request);
    }

    protected MiraklRequestAdditionalFieldValue.MiraklSimpleRequestAdditionalFieldValue createAdditionalField(String code, String value) {
        return new MiraklRequestAdditionalFieldValue.MiraklSimpleRequestAdditionalFieldValue(code, value);
    }
}
