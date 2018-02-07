package com.adyen.mirakl.cucumber.stepdefs.hooks;

import cucumber.api.java.Before;

import static com.adyen.mirakl.cucumber.apiobjects.BaseApi.initApi;

public class Hooks {
    @Before
    public static void setupRequestEndpoint() {
        initApi();
    }
}
