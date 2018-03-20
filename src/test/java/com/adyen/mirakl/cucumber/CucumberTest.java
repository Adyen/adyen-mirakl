package com.adyen.mirakl.cucumber;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import cucumber.api.testng.CucumberFeatureWrapper;
import cucumber.api.testng.TestNGCucumberRunner;
import cucumber.runtime.model.CucumberFeature;
import org.assertj.core.api.Assertions;
import org.junit.runner.RunWith;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = "pretty", features = "src/test/features", tags = {"~@bug", "@cucumber", "~@exclude"})
public class CucumberTest extends StepDefsHelper {

    private TestNGCucumberRunner testNGCucumberRunner;

    @BeforeClass(alwaysRun = true)
    public void setUpClass() {
        testNGCucumberRunner = new TestNGCucumberRunner(this.getClass());
    }

    @Test(description = "Runs smoke test")
    public void smokeTest() {
        List<CucumberFeature> features = testNGCucumberRunner.getFeatures();
        CucumberFeature feature = null;
        for (CucumberFeature cucumberFeature : features) {
            if (cucumberFeature.getGherkinFeature().getId().equalsIgnoreCase("smoke-test")){
                feature = cucumberFeature;
                break;
            }
        }
        Assertions.assertThat(feature).isNotNull();
        testNGCucumberRunner.runCucumber(feature);
    }

    @Test(dependsOnMethods = {"smokeTest"}, testName = "cucumber", description = "Runs Cucumber Features", dataProvider = "features")
    public void feature(CucumberFeatureWrapper cucumberFeature) {
        testNGCucumberRunner.runCucumber(cucumberFeature.getCucumberFeature());
    }

    @DataProvider
    public Object[][] features() {
        return testNGCucumberRunner.provideFeatures();
    }

    @AfterClass(alwaysRun = true)
    public void tearDownClass() {
        testNGCucumberRunner.finish();
    }

}
