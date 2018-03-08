package com.adyen.mirakl.cucumber.stepdefs.helpers.restassured;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import io.restassured.RestAssured;
import io.restassured.response.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RestAssuredAdyenApi {

    private static final Logger log = LoggerFactory.getLogger(RestAssuredAdyenApi.class);

    public Map<String, Object> getAdyenNotificationBody(String endpoint, String miraklShopId, String eventType, String verificationType) {
        ResponseBody body = getResponseBody(endpoint);
        List<String> check = body.jsonPath().get("body");
        if (! CollectionUtils.isEmpty(check) ) {
            for (String list : check) {

                Map<String, Object> mapResult = new HashMap<>(new Gson().fromJson(list, new TypeToken<HashMap<String, Object>>() {
                }.getType()));

                final Map contentMap = (Map) mapResult.get("content");
                // if verificationType, accountHolderCode and verificationType have been provided
                // by cucumber and they match the notification in endpoint then return that notification
                // else if only accountHolderCode and eventType have been provided then return that notification
                if (contentMap.get("verificationType") != null && verificationType != null) {
                    if (contentMap.get("accountHolderCode").equals(miraklShopId)
                        && mapResult.get("eventType").equals(eventType)
                        && contentMap.get("verificationType").equals(verificationType)) {
                        log.info("found from url: {} : {}",endpoint, list);
                        return mapResult;
                    }
                } else {
                    if (contentMap.get("accountHolderCode").equals(miraklShopId) && mapResult.get("eventType").equals(eventType)) {
                        log.info("found from url: {} : {}",endpoint, list);
                        return mapResult;
                    }
                }
            }
        }
        return null;
    }

    public boolean endpointHasANotification(String endpoint) {
        return !"[]".equals(getResponseBody(endpoint).asString());
    }

    public ResponseBody getResponseBody(String endpoint) {
        return RestAssured.get(endpoint).getBody();
    }
}
