package com.adyen.mirakl.cucumber.apiobjects

import com.adyen.mirakl.cucumber.support.mirakl.api.MiraklApiCalls
import org.assertj.core.api.Assertions

class MiraklShops {
    static void verifyShopDocumentSuccessfullyUploaded() {
        def file = "/home/admin/workspace/SpiceAutomationTests/src/test/resources/documents/blah.txt"
        def documents = "<body>\n" +
                "  <shop_documents>\n" +
                "    <shop_document>\n" +
                "      <file_name>blah.txt</file_name>\n" +
                "      <type_code>passport-scan</type_code>\n" +
                "    </shop_document>\n" +
                "  </shop_documents>\n" +
                "</body>"

        int uploadDocResponse = Integer.parseInt(MiraklApiCalls.s32UploadDocument("2019", file, documents).jsonPath().get("errors_count").toString())
        Assertions.assertThat(uploadDocResponse).isEqualTo(0)
    }
}
