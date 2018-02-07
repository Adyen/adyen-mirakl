package com.adyen.mirakl.cucumber.apiobjects

import com.adyen.mirakl.cucumber.support.ReadConfig
import com.google.gson.Gson
import io.restassured.RestAssured
import io.restassured.response.Response
import io.restassured.response.ResponseBody

class BaseApi {

    // base endpoint url
    static String baseUrl() {
        return RestAssured.baseURI = ReadConfig.readConfig("mirakl_url")
    }

    // base path
    static String basePath() {
        return RestAssured.basePath = ReadConfig.readConfig("mirakl_base_path")
    }

    // TODO: api key - needs to come from Env Variable
    static String apiKey() {
        return ReadConfig.readConfig("mirakl_operator_api_key")
    }

    static void initApi() {
        baseUrl()
        basePath()
    }

    // GET requests
    static Response httpGetRequest(def endpoint) {
        return RestAssured
                .given().relaxedHTTPSValidation()
                .get("${endpoint}api_key=${apiKey()}")
    }

    // PUT requests
    static boolean httpPutRequest(def endpoint, Object payload) {
        payload = new Gson().toJson(payload, Object.class)
        return RestAssured
                .given().headers('Content-Type': 'application/json', accept: 'application/json').body(payload)
                .when().put("${endpoint}api_key=${apiKey()}")
                .then().statusCode(204)
    }

    // POST requests
    static ResponseBody httpPostRequest(String endpoint, Object payload, String shopId, String filePath) {
        return RestAssured
                .given().headers(accept: 'application/json')
                    .multiPart("files", new File(filePath))
                    .formParam("shop_id", shopId)
                    .formParam("shop_documents", payload)
                .when()
                    .post("${endpoint}api_key=${apiKey()}")
                .thenReturn().body()
    }
}
