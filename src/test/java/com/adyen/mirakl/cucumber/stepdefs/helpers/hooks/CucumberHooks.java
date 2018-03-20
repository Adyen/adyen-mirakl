package com.adyen.mirakl.cucumber.stepdefs.helpers.hooks;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import cucumber.api.java.Before;
import org.awaitility.Awaitility;
import org.awaitility.Duration;

public class CucumberHooks extends StepDefsHelper {

    @Before
    public void setDefaultAwaitilityTimeOut() {
        Awaitility.setDefaultTimeout(Duration.FIVE_MINUTES);
    }

    @Before
    public void clearCucumberMap() {
        cucumberMap.clear();
    }
}
