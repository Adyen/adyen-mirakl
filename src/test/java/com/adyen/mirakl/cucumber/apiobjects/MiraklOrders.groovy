package com.adyen.mirakl.cucumber.apiobjects

import com.adyen.mirakl.cucumber.support.mirakl.api.MiraklApiCalls
import com.jayway.awaitility.Awaitility
import groovy.json.JsonSlurper
import io.restassured.response.Response
import org.assertj.core.api.Assertions
import java.util.concurrent.TimeUnit

class MiraklOrders {

    static void verifyOrderExists(def orderNumber) {
        int orderCount = MiraklApiCalls.or11GetOrderResponse(orderNumber).jsonPath().get('total_count') // cast as integer to compare
        Assertions.assertThat(orderCount).isGreaterThan(0)
    }

    static void verifyOrderStatus(String orderStatus, def orderNumber) {
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .until() {
            Automation.support.mirakl.api.MiraklApiCalls.or11GetOrderResponse(orderNumber).jsonPath().get('orders.order_state').toString().contains(orderStatus)
        }
        Assertions.assertThat(orderStatus).contains(MiraklApiCalls.or11GetOrderResponse(orderNumber).jsonPath().get('orders.order_state').toString().join(""))
    }

    static void acceptOrder(def orderNumber) {
        Response orderResponse = MiraklApiCalls.or11GetOrderResponse(orderNumber)
        def orderLineNumber = orderResponse.jsonPath().get('orders.order_lines.order_line_id[0][0]')
        def json = new JsonSlurper()
        String payloadAsString = '{"order_lines":[{"accepted": true, "id": "' + orderLineNumber + '"}]}'
        def jsonObject = json.parseText(payloadAsString)
        MiraklApiCalls.or21AcceptOrder(orderLineNumber.toString().substring(0, 15), jsonObject)
    }
}
