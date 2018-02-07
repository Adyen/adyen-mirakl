package com.adyen.mirakl.cucumber.support.mirakl.api

import com.adyen.mirakl.cucumber.apiobjects.BaseApi
import io.restassured.response.Response
import io.restassured.response.ResponseBody

class MiraklApiCalls extends BaseApi {

    // OR11 - List Orders
    static Response or11GetOrderResponse(def orderNumber) {
        return httpGetRequest("orders?commercial_ids=$orderNumber&")
    }

    // OR21 - Accept or refuse order lines which are in "WAITING_ACCEPTANCE" state
    static void or21AcceptOrder(def orderNumber, Object acceptBean) {
        httpPutRequest("orders/$orderNumber/accept?", acceptBean)
    }

    // S32 - Upload documents to associate to a shop
    static ResponseBody s32UploadDocument(String shopId, String file, String documents){
        return httpPostRequest("shops/documents?", documents, shopId, file)
    }
}
