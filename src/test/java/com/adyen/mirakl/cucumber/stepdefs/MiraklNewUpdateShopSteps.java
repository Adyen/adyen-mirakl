package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.mirakl.client.mmp.operator.domain.shop.create.MiraklCreatedShops;
import cucumber.api.DataTable;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.When;

import java.util.List;
import java.util.Map;

public class MiraklNewUpdateShopSteps extends StepDefsHelper {

    @Given("^a shop has been created in Mirakl for an (.*) with Bank Information$")
    public void aShopHasBeenCreatedInMiraklForAnIndividualWithBankInformation(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi
            .createShopForIndividualWithBankDetails(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        world.miraklShop = retrieveCreatedShop(shops);
    }

    @Given("^a new shop has been created in Mirakl for an (.*)$")
    public void aNewShopHasBeenCreatedInMiraklForAnIndividual(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createShopForIndividual(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        world.miraklShop = retrieveCreatedShop(shops);
    }

    @Given("^a shop has been created in Mirakl for an (.*) with mandatory KYC data$")
    public void aShopHasBeenCreatedInMiraklForAnIndividualWithMandatoryKYCData(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createShopForIndividualWithBankDetails(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        world.miraklShop = retrieveCreatedShop(shops);
    }

    @When("^a new shop has been created in Mirakl for a (.*)")
    public void aNewShopHasBeenCreatedInMiraklForABusiness(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithNoUBOs(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        world.miraklShop  = retrieveCreatedShop(shops);
    }

    @When("^a new shop has been created in Mirakl with UBO Data for a (.*)")
    public void aNewShopHasBeenCreatedAsABusinessWithUBOData(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithFullUboInfo(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        world.miraklShop = retrieveCreatedShop(shops);
    }

    @Given("^a new (.*) shop has been created in Mirakl without mandatory Shareholder Information$")
    public void aNewBusinessShopHasBeenCreatedInMiraklWithoutMandatoryShareholderInformation(String legalEntity, DataTable table) throws Throwable {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithMissingUboInfo(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        world.miraklShop = retrieveCreatedShop(shops);
    }

    @Given("^a new (.*) shop has been created in Mirakl with invalid data$")
    public void aNewBusinessShopHasBeenCreatedInMiraklWithInvalidData(String legalEntity, DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        MiraklCreatedShops shops = miraklShopApi.createBusinessShopWithMissingUboInfo(miraklMarketplacePlatformOperatorApiClient, cucumberTable, legalEntity);
        world.miraklShop = retrieveCreatedShop(shops);
    }

    @When("^the IBAN has been modified in Mirakl$")
    public void theIBANHasBeenModifiedInMirakl(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        world.miraklShop = miraklUpdateShopApi
            .updateShopsIbanNumberOnly(world.miraklShop, world.miraklShop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }

    @And("^a new IBAN has been provided by the seller in Mirakl and the mandatory IBAN fields have been provided$")
    public void aNewIBANHasBeenProvidedByTheSellerInMiraklAndTheMandatoryIBANFieldsHaveBeenProvided() {
        world.miraklShop = miraklUpdateShopApi
            .updateShopToAddBankDetails(world.miraklShop, world.miraklShop.getId(), miraklMarketplacePlatformOperatorApiClient);
    }

    @And("^Mirakl has been updated with a taxId$")
    public void miraklHasBeenUpdatedWithATaxId() {
        world.miraklShop = miraklUpdateShopApi
            .updateShopToIncludeVATNumber(world.miraklShop, world.miraklShop.getId(), miraklMarketplacePlatformOperatorApiClient);
    }

    @When("^we update the shop by adding more shareholder data$")
    public void weUpdateTheShopByAddingMoreShareholderData(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        world.miraklShop = miraklUpdateShopApi
            .addMoreUbosToShop(world.miraklShop, world.miraklShop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }

    @When("^the shareholder data has been updated in Mirakl$")
    public void theShareholderDataHasBeenUpdatedInMirakl(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        world.miraklShop = miraklUpdateShopApi
            .updateUboData(world.miraklShop, world.miraklShop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }

    @When("^the Mirakl Shop Details have been updated$")
    public void theMiraklShopDetailsHaveBeenUpdated(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        cucumberTable.forEach(row ->
            world.miraklShop = miraklUpdateShopApi
                .updateExistingShopsContactInfoWithTableData(world.miraklShop, world.miraklShop.getId(), miraklMarketplacePlatformOperatorApiClient, row)
        );
    }

    @When("^the Mirakl Shop Details have been changed")
    public void theMiraklShopDetailsHaveBeenchanged() {
        world.miraklShop = miraklUpdateShopApi
            .updateExistingShopAddressFirstLine(world.miraklShop, world.miraklShop.getId(), miraklMarketplacePlatformOperatorApiClient);
    }

    @When("^the Mirakl Shop Details have been updated with invalid data$")
    public void theMiraklShopDetailsHaveBeenUpdatedWithInvalidData(DataTable table) {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        world.miraklShop = miraklUpdateShopApi
            .updateUboDataWithInvalidData(world.miraklShop, world.miraklShop.getId(), miraklMarketplacePlatformOperatorApiClient, cucumberTable);
    }
}
