package com.adyen.mirakl.cucumber.stepdefs;

import com.adyen.mirakl.cucumber.stepdefs.helpers.stepshelper.StepDefsHelper;
import com.adyen.mirakl.web.rest.MiraklNotificationsResource;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import cucumber.api.DataTable;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConnectorAppSteps extends StepDefsHelper {

    @Autowired
    private MiraklNotificationsResource miraklNotificationsResource;

    private MockMvc restUserMockMvc;

    @Before
    public void setup() {
        this.restUserMockMvc = MockMvcBuilders.standaloneSetup(miraklNotificationsResource).build();
    }

    @When("^a payment voucher is sent to the Connector$")
    public void aPaymentVoucherIsSentToAdyen(DataTable table) throws Exception {
        List<Map<String, String>> cucumberTable = table.getTableConverter().toMaps(table, String.class, String.class);
        String paymentVoucher = cucumberTable.get(0).get("paymentVoucher");
        URL url = Resources.getResource("paymentvouchers/"+paymentVoucher);
        final String csvFile = Resources.toString(url, Charsets.UTF_8);

        MockMultipartFile mockMultipartFile = new MockMultipartFile("file", paymentVoucher, "text/plain", csvFile.getBytes());

        restUserMockMvc.perform(MockMvcRequestBuilders.fileUpload("/api/mirakl-notifications/payout")
            .file(mockMultipartFile))
            .andExpect(status().is(200));
    }

    @Then("^we process the data and push to Adyen$")
    public void adyenWillProcessTheData() {
        shopService.processUpdatedShops();
    }

    @And("^we process the document data and push to Adyen$")
    public void weProcessTheDocumentDataAndPushToAdyen() {
        docService.retrieveBankproofAndUpload();
    }

}
