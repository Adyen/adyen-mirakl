package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;

public class ConnectorAppSteps extends StepDefsHelper {

    @Then("^the connector processes the data and pushes to Adyen$")
    public void theConnectorProcessesTheDataAndPushesToAdyen() {
        shopService.processUpdatedShops();
    }

    @And("^the connector processes the document data and push to Adyen$")
    public void weProcessTheDocumentDataAndPushToAdyen() {
        docService.processUpdatedDocuments();
    }

    @Then("^the Connector will trigger payout retry$")
    public void theConnectorWillTriggerPayoutRetry() {
        retryPayoutService.retryFailedPayouts();
    }
}
