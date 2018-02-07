package com.adyen.mirakl.cucumber;

import org.junit.runner.RunWith;
import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(
    plugin = "pretty",
    glue = "com/adyen/mirakl/cucumber/stepdefs",
    features = "src/test/features")
public class CucumberTest  {
}
