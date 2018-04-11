package com.adyen.mirakl.cucumber;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import cucumber.api.testng.CucumberFeatureWrapper;
import cucumber.api.testng.TestNGCucumberRunner;
import cucumber.runtime.model.CucumberFeature;
import org.junit.runner.RunWith;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = "pretty", features = "src/test/features", tags = {"~@bug", "~@exclude"})
public class CucumberTest {

    private TestNGCucumberRunner testNGCucumberRunner;

    @BeforeClass(alwaysRun = true)
    public void setUpClass() {
        testNGCucumberRunner = new TestNGCucumberRunner(this.getClass());
    }

    @Test(suiteName = "cucumber")
    public void smokeTest() {
        runTaggedFeature(allFeatures(), "@smoke");
    }

    @Test(dependsOnMethods = "smokeTest", suiteName = "cucumber", dataProvider = "features")
    public void feature(CucumberFeatureWrapper featureWrapper) {
      testNGCucumberRunner.runCucumber(featureWrapper.getCucumberFeature());
    }

    @DataProvider
    private Object[][] features() {
        return testNGCucumberRunner.provideFeatures();
    }

    private void runTaggedFeature(List<CucumberFeature> features, String cucumberTag) {
        features.forEach(feature -> {
            boolean match = feature.getGherkinFeature().getTags().stream()
                .anyMatch(tag -> tag.getName().equals(cucumberTag));
            if (match) {
                testNGCucumberRunner.runCucumber(feature);
            }
        });
    }

    private List<CucumberFeature> allFeatures() {
        return testNGCucumberRunner.getFeatures();
    }

    @AfterClass(alwaysRun = true)
    public void tearDownClass() {
        testNGCucumberRunner.finish();
    }
}
