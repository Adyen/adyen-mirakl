package com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi;

import com.google.common.collect.ImmutableList;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreateShop;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShopReturn;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import com.mirakl.client.mmp.operator.request.shop.MiraklCreateShopsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MiraklShopApi extends MiraklShopProperties {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private ImmutableList.Builder<MiraklCreateShop> miraklShopCreateBuilder(MiraklCreateShop miraklCreateShop) {
        ImmutableList.Builder<MiraklCreateShop> builder = new ImmutableList.Builder<>();
        builder.add(miraklCreateShop);
        return builder;
    }

    // Individual Shop
    public MiraklCreatedShops createShopForIndividual(MiraklMarketplacePlatformOperatorApiClient client, List<Map<String, String>> rows, String legalEntity) {
        MiraklCreateShop miraklCreateShop = new MiraklCreateShop();
        miraklCreateShop = populateMiraklShop(rows, legalEntity, miraklCreateShop);
        ImmutableList.Builder<MiraklCreateShop> miraklCreateShopBuilder = miraklShopCreateBuilder(miraklCreateShop);
        return createMiraklShopRequest(client, miraklCreateShopBuilder);
    }

    // Individual Shop with Bank Details
    public MiraklCreatedShops createShopForIndividualWithBankDetails(MiraklMarketplacePlatformOperatorApiClient client, List<Map<String, String>> rows, String legalEntity) {
        MiraklCreateShop miraklCreateShop = new MiraklCreateShop();
        miraklCreateShop = populateMiraklShop(rows, legalEntity, miraklCreateShop);
        ImmutableList.Builder<MiraklCreateShop> miraklCreateShopBuilder = miraklShopCreateBuilder(miraklCreateShop);
        populatePaymentInformation(rows, miraklCreateShop);
        return createMiraklShopRequest(client, miraklCreateShopBuilder);
    }

    // Business shop is created with 1 UBO created in non-sequential order
    public MiraklCreatedShops createBusinessShopWithNonSequentialUBO(MiraklMarketplacePlatformOperatorApiClient client, List<Map<String, String>> rows, String legalEntity) {
        MiraklCreateShop miraklCreateShop = new MiraklCreateShop();
        miraklCreateShop = populateMiraklShop(rows, legalEntity, miraklCreateShop);
        populateShareholderInNonSequentialOrder(legalEntity, rows, miraklCreateShop);
        ImmutableList.Builder<MiraklCreateShop> miraklCreateShopBuilder = miraklShopCreateBuilder(miraklCreateShop);
        return createMiraklShopRequest(client, miraklCreateShopBuilder);
    }

    // Business with UBOs populated, amount of UBOs come from Cucumber tables, full UBO data is provided in this method
    public MiraklCreatedShops createBusinessShopWithFullUboInfo(MiraklMarketplacePlatformOperatorApiClient client, List<Map<String, String>> rows, String legalEntity) {
        MiraklCreateShop miraklCreateShop = new MiraklCreateShop();
        miraklCreateShop = populateMiraklShop(rows, legalEntity, miraklCreateShop);
        populateShareHolderData(legalEntity, rows, miraklCreateShop);
        ImmutableList.Builder<MiraklCreateShop> miraklCreateShopBuilder = miraklShopCreateBuilder(miraklCreateShop);
        return createMiraklShopRequest(client, miraklCreateShopBuilder);
    }

    // Business with UBOs populated, amount of UBOs come from Cucumber tables, missing UBO data is provided in this method
    public MiraklCreatedShops createBusinessShopWithMissingUboInfo(MiraklMarketplacePlatformOperatorApiClient client, List<Map<String, String>> rows, String legalEntity) {
        MiraklCreateShop miraklCreateShop = new MiraklCreateShop();
        miraklCreateShop = populateMiraklShop(rows, legalEntity, miraklCreateShop);
        populateShareholderWithMissingData(legalEntity, rows, miraklCreateShop);
        ImmutableList.Builder<MiraklCreateShop> miraklCreateShopBuilder = miraklShopCreateBuilder(miraklCreateShop);
        return createMiraklShopRequest(client, miraklCreateShopBuilder);
    }

    // used for Netherlands shops
    public MiraklCreatedShops createBusinessShopForNetherlandsWithUBOs(MiraklMarketplacePlatformOperatorApiClient client, List<Map<String, String>> rows, String legalEntity){
        MiraklCreateShop miraklCreateShop = new MiraklCreateShop();
        miraklCreateShop = populateMiraklShopForNetherlands(rows, miraklCreateShop);
        populateShareHolderDataForNetherlands(legalEntity, rows, miraklCreateShop);
        populatePaymentInformation(rows, miraklCreateShop);
        ImmutableList.Builder<MiraklCreateShop> miraklCreateShopBuilder = miraklShopCreateBuilder(miraklCreateShop);
        return createMiraklShopRequest(client, miraklCreateShopBuilder);
    }

    public MiraklCreatedShops createBusinessShopWithNoUBOs(MiraklMarketplacePlatformOperatorApiClient client, List<Map<String, String>> rows, String legalEntity) {
        MiraklCreateShop miraklCreateShop = new MiraklCreateShop();
        miraklCreateShop = populateMiraklShop(rows, legalEntity, miraklCreateShop);
        ImmutableList.Builder<MiraklCreateShop> miraklCreateShopBuilder = miraklShopCreateBuilder(miraklCreateShop);
        return createMiraklShopRequest(client, miraklCreateShopBuilder);
    }

    // Mandatory for any type of shop creation
    private MiraklCreateShop populateMiraklShop(List<Map<String, String>> rows, String legalEntity, MiraklCreateShop miraklCreateShop){
        populateMiraklAddress(rows, miraklCreateShop);
        populateMiraklProfessionalInformation(miraklCreateShop);
        populateUserEmailAndShopName(miraklCreateShop, rows);
        populateAddFieldsLegalAndHouseNumber(legalEntity, miraklCreateShop);
        return miraklCreateShop;
    }

    // Mandatory for any type of shop creation for Individual
    private MiraklCreateShop populateMiraklShopForNetherlands(List<Map<String, String>> rows, MiraklCreateShop miraklCreateShop){
        populateMiraklAddressForNetherlands(miraklCreateShop);
        populateMiraklProfessionalInformation(miraklCreateShop);
        populateUserEmailAndShopName(miraklCreateShop, rows);
        return miraklCreateShop;
    }

    private MiraklCreatedShops createMiraklShopRequest(MiraklMarketplacePlatformOperatorApiClient client, ImmutableList.Builder<MiraklCreateShop> miraklCreateShopBuilder) {
        MiraklCreateShopsRequest miraklCreateShopsRequest = new MiraklCreateShopsRequest(miraklCreateShopBuilder.build());
        MiraklCreatedShops shops = client.createShops(miraklCreateShopsRequest);
        throwErrorIfShopIsNotCreated(shops);
        shops.getShopReturns().forEach(MiraklCreatedShopReturn::getShopCreated);
        return shops;
    }

}
