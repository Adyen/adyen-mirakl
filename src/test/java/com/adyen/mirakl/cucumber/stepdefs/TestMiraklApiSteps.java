package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.apiobjects.MiraklOrders;
import com.adyen.mirakl.cucumber.apiobjects.MiraklShops;
import com.adyen.mirakl.cucumber.stepdefs.hooks.Hooks;
import cucumber.api.DataTable;
import cucumber.api.java.Before;
import cucumber.api.java8.En;

import java.util.List;
import java.util.Map;

public class TestMiraklApiSteps implements En {
    private final String orderNumber = "1517563255280";
    private List<Map<String, String>> tableList = null;

    @Before // always need a Before tag even if empty
    public void setup() {
        Hooks.setupRequestEndpoint();
    }

    public TestMiraklApiSteps() {
        When("^an order is accepted in Mirakl$", () -> {
            MiraklOrders.verifyOrderExists(orderNumber);
            MiraklOrders.verifyOrderStatus("RECEIVED", orderNumber);
        });
        When("^the order is shipped in Mirakl$", () -> {
            MiraklOrders.acceptOrder(orderNumber);
        });
        Then("^the order status will change$", (DataTable dataTable) -> {
            tableList = dataTable.asMaps(String.class, String.class);
            MiraklOrders.verifyOrderStatus(tableList.get(0).get("Order Status"), orderNumber);
        });
        Given("^the operator has updated the sellers details in Mirakl$", () -> {
            MiraklShops.verifyShopDocumentSuccessfullyUploaded();
        });
    }
}
