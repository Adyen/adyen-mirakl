package com.adyen.mirakl.cucumber.stepdefs.helpers.miraklapi;

import com.google.common.collect.ImmutableList;
import com.mirakl.client.mmp.domain.shop.MiraklProfessionalInformation;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShopAddress;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.operator.domain.shop.update.MiraklUpdateShop;
import com.mirakl.client.mmp.operator.domain.shop.update.MiraklUpdatedShopReturn;
import com.mirakl.client.mmp.operator.domain.shop.update.MiraklUpdatedShops;
import com.mirakl.client.mmp.operator.request.shop.MiraklUpdateShopsRequest;
import com.mirakl.client.mmp.request.additionalfield.MiraklRequestAdditionalFieldValue.MiraklSimpleRequestAdditionalFieldValue;
import com.mirakl.client.mmp.request.shop.document.MiraklUploadShopDocumentsRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MiraklUpdateShopApi extends MiraklUpdateShopProperties {

    private ImmutableList.Builder<MiraklUpdateShop> miraklUpdateShopBuilder(MiraklUpdateShop miraklUpdateShop) {
        ImmutableList.Builder<MiraklUpdateShop> builder = new ImmutableList.Builder<>();
        builder.add(miraklUpdateShop);
        return builder;
    }

    public MiraklShop addMoreUbosToShop(MiraklShop miraklShop, String shopId, MiraklMarketplacePlatformOperatorApiClient client, List<Map<String, String>> rows) {
        MiraklUpdateShop miraklUpdateShop = new MiraklUpdateShop();
        miraklUpdateShop = populateAllMandatoryFields(miraklShop, shopId, miraklUpdateShop);
        // add Ubos
        ImmutableList.Builder<MiraklSimpleRequestAdditionalFieldValue> additionalUbosList = addMiraklShopUbos(rows);
        populateMiraklAdditionalFields(miraklUpdateShop, miraklShop, additionalUbosList.build());
        ImmutableList.Builder<MiraklUpdateShop> miraklUpdateShopBuilder = miraklUpdateShopBuilder(miraklUpdateShop);
        return updateMiraklRequest(client, miraklUpdateShopBuilder);
    }

    public MiraklShop updateUboData(MiraklShop miraklShop, String shopId, MiraklMarketplacePlatformOperatorApiClient client, List<Map<String, String>> rows){
        MiraklUpdateShop miraklUpdateShop = new MiraklUpdateShop();
        miraklUpdateShop = populateAllMandatoryFields(miraklShop, shopId, miraklUpdateShop);

        // update UBO data
        ImmutableList.Builder<MiraklSimpleRequestAdditionalFieldValue> updatedShopUboList = updateMiraklShopUbos(rows);
        populateMiraklAdditionalFields(miraklUpdateShop, miraklShop, updatedShopUboList.build());

        ImmutableList.Builder<MiraklUpdateShop> miraklUpdateShopBuilder = miraklUpdateShopBuilder(miraklUpdateShop);
        return updateMiraklRequest(client, miraklUpdateShopBuilder);
    }

    public MiraklShop updateUboDataWithInvalidData(MiraklShop miraklShop, String shopId, MiraklMarketplacePlatformOperatorApiClient client, List<Map<String, String>> rows){
        MiraklUpdateShop miraklUpdateShop = new MiraklUpdateShop();
        miraklUpdateShop = populateAllMandatoryFields(miraklShop, shopId, miraklUpdateShop);

        // update UBO data
        ImmutableList.Builder<MiraklSimpleRequestAdditionalFieldValue> updatedShopUboList = updateMiraklShopUbosWithInvalidData(rows);
        populateMiraklAdditionalFields(miraklUpdateShop, miraklShop, updatedShopUboList.build());

        ImmutableList.Builder<MiraklUpdateShop> miraklUpdateShopBuilder = miraklUpdateShopBuilder(miraklUpdateShop);
        return updateMiraklRequest(client, miraklUpdateShopBuilder);
    }

    public MiraklShop updateExistingShopsContactInfoWithTableData(MiraklShop miraklShop, String shopId, MiraklMarketplacePlatformOperatorApiClient client, Map<String, String> row) {
        MiraklUpdateShop miraklUpdateShop = new MiraklUpdateShop();
        miraklUpdateShop = populateAllMandatoryFields(miraklShop, shopId, miraklUpdateShop);

        // update shop contact information
        MiraklShopAddress address = updateMiraklShopAddress(miraklShop, row);
        miraklUpdateShop.setAddress(address);

        ImmutableList.Builder<MiraklUpdateShop> miraklUpdateShopBuilder = miraklUpdateShopBuilder(miraklUpdateShop);
        return updateMiraklRequest(client, miraklUpdateShopBuilder);
    }

    public MiraklShop updateShopToIncludeVATNumber(MiraklShop miraklShop, String shopId, MiraklMarketplacePlatformOperatorApiClient client) {
        MiraklUpdateShop miraklUpdateShop = new MiraklUpdateShop();
        miraklUpdateShop = populateAllMandatoryFields(miraklShop, shopId, miraklUpdateShop);
        // update VAT number:
        MiraklProfessionalInformation miraklProfessionalInformation = updateMiraklShopTaxId(miraklShop);
        miraklUpdateShop.setProfessionalInformation(miraklProfessionalInformation);
        ImmutableList.Builder<MiraklUpdateShop> miraklUpdateShopBuilder = miraklUpdateShopBuilder(miraklUpdateShop);
        return updateMiraklRequest(client, miraklUpdateShopBuilder);
    }

    public MiraklShop updateShopToAddBankDetails(MiraklShop miraklShop, String shopId, MiraklMarketplacePlatformOperatorApiClient client) {
        MiraklUpdateShop miraklUpdateShop = new MiraklUpdateShop();
        miraklUpdateShop = populateAllMandatoryFields(miraklShop, shopId, miraklUpdateShop);
        ImmutableList.Builder<MiraklUpdateShop> miraklUpdateShopBuilder = miraklUpdateShopBuilder(miraklUpdateShop);
        return updateMiraklRequest(client, miraklUpdateShopBuilder);
    }

    public MiraklShop updateShopsIbanNumberOnly(MiraklShop miraklShop, String shopId, MiraklMarketplacePlatformOperatorApiClient client, List<Map<String, String>> rows) {
        MiraklUpdateShop miraklUpdateShop = new MiraklUpdateShop();
        miraklUpdateShop = populateAllMandatoryFields(miraklShop, shopId, miraklUpdateShop);

        // update new iban number only:
        MiraklIbanBankAccountInformation paymentInformation = updateNewMiraklIbanOnly(miraklShop, rows);
        miraklUpdateShop.setPaymentInformation(paymentInformation);

        ImmutableList.Builder<MiraklUpdateShop> miraklUpdateShopBuilder = miraklUpdateShopBuilder(miraklUpdateShop);
        return updateMiraklRequest(client, miraklUpdateShopBuilder);
    }

    public void uploadBankStatementToExistingShop(String shopId, MiraklMarketplacePlatformOperatorApiClient client) {
        MiraklUpdateShop miraklUpdateShop = new MiraklUpdateShop();
        miraklUpdateShopBuilder(miraklUpdateShop);
        // upload bankStatement
        MiraklUploadShopDocumentsRequest miraklUploadShopDocumentsRequest = uploadMiraklShopWithBankStatement(shopId);
        client.uploadShopDocuments(miraklUploadShopDocumentsRequest);
    }

    // required for any update we do to Mirakl
    private MiraklUpdateShop populateAllMandatoryFields(MiraklShop miraklShop, String shopId, MiraklUpdateShop miraklUpdateShop) {
        miraklUpdateShop.setShopId(Long.valueOf(shopId));
        MiraklIbanBankAccountInformation paymentInformation = populateMiraklIbanBankAccountInformation(miraklShop);
        miraklUpdateShop.setPaymentInformation(paymentInformation);
        MiraklShopAddress address = populateMiraklShopAddress(miraklShop);
        miraklUpdateShop.setAddress(address);
        populateShopNameAndEmail(miraklShop, miraklUpdateShop);
        populateMiraklChannel(miraklShop, miraklUpdateShop);
        populateMiraklShopPremiumSuspendAndPaymentBlockedStatus(miraklShop, miraklUpdateShop);

        // List will be used to define additional fields that require updates/changes
        ImmutableList.Builder<MiraklSimpleRequestAdditionalFieldValue> fieldsToUpdate = new ImmutableList.Builder<>();
        populateMiraklAdditionalFields(miraklUpdateShop, miraklShop, fieldsToUpdate.build());
        return miraklUpdateShop;
    }

    private MiraklShop updateMiraklRequest(MiraklMarketplacePlatformOperatorApiClient client, ImmutableList.Builder<MiraklUpdateShop> builder) {
        MiraklUpdateShopsRequest request = new MiraklUpdateShopsRequest(builder.build());
        final MiraklUpdatedShops miraklUpdatedShopsResponse = client.updateShops(request);
        throwErrorIfShopFailedToUpdate(miraklUpdatedShopsResponse);
        return miraklUpdatedShopsResponse.getShopReturns().stream().map(MiraklUpdatedShopReturn::getShopUpdated).findFirst().orElse(null);
    }
}

