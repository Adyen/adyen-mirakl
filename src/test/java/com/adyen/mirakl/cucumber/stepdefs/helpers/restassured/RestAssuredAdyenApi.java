package com.adyen.mirakl.cucumber.stepdefs.helpers.restassured;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.restassured.RestAssured;
import io.restassured.response.ResponseBody;
import org.assertj.core.api.Assertions;
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
        if (!CollectionUtils.isEmpty(check)) {
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
                        log.info("found from url: {} : {}", endpoint, list);
                        return mapResult;
                    }
                } else if (contentMap.get("accountHolderCode") != null) {
                    if (contentMap.get("accountHolderCode").equals(miraklShopId) && mapResult.get("eventType").equals(eventType)) {
                        log.info("found from url: {} : {}", endpoint, list);
                        return mapResult;
                    }
                }
            }
        }
        return null;
    }

    public ImmutableList<DocumentContext> getMultipleAdyenNotificationBodies(String endpoint, String miraklShopId, String eventType, String verificationType) {
        ResponseBody body = getResponseBody(endpoint);
        List<String> allNotifications = body.jsonPath().get("body");
        ImmutableList.Builder<DocumentContext> notifications = new ImmutableList.Builder<>();
        // filter through all notifications and add all that match criteria to String List
        for (String notification : allNotifications) {
            Map contentMap = JsonPath.parse(notification).read("content");
            DocumentContext content = JsonPath.parse(contentMap);
            Assertions.assertThat(JsonPath.parse(notification).read("eventType").toString()).isNotEmpty();
            if (verificationType != null) {
                if (JsonPath.parse(notification).read("eventType").toString().equals(eventType) &&
                    content.read("accountHolderCode").equals(miraklShopId) &&
                    content.read("verificationType").equals(verificationType)) {
                    notifications.add(JsonPath.parse(notification));
                }
            } else {
                if (JsonPath.parse(notification).read("eventType").toString().equals(eventType) &&
                    content.read("accountHolderCode").equals(miraklShopId)) {
                    notifications.add(JsonPath.parse(notification));
                }
            }

        }
        return notifications.build();
    }

    public ImmutableList<DocumentContext> getMultipleAdyenTransferNotifications(String endpoint, String eventType, String transferCode) {
        ResponseBody body = getResponseBody(endpoint);
        List<String> allNotifications = body.jsonPath().get("body");

        ImmutableList.Builder<DocumentContext> notifications = new ImmutableList.Builder<>();
        allNotifications.forEach(notification -> {
            DocumentContext jsonBody = JsonPath.parse(notification);
            if (jsonBody.read("eventType").toString().equals(eventType) &&
                jsonBody.read("content.transferCode").toString().equals(transferCode)) {
                notifications.add(jsonBody);
            }
        });
        return notifications.build();
    }

    public ImmutableList<DocumentContext> extractShareHolderNotifications(List<DocumentContext> notifications, List<String> shareholderCodes) {
        // filter through String List of notifications to see if shareholderCode matches
        // add all that match to list builder
        Integer counter = 0;
        ImmutableList.Builder<DocumentContext> notificationsBuilder = new ImmutableList.Builder<>();
        for (DocumentContext notification : notifications) {
            if (notification.read("content.shareholderCode") != null) {
                for (String shareholderCode : shareholderCodes) {
                    if (notification.read("content.shareholderCode").toString().equals(shareholderCode)) {
                        notificationsBuilder.add(notification);
                        counter++;
                        if(shareholderCodes.size() == counter){
                            return notificationsBuilder.build();
                        }
                    }
                }
            }
        }
        return notificationsBuilder.build();
    }

    public DocumentContext extractCorrectTransferNotification(DocumentContext notification, String sourceAccountCode, String destAccountCode) {
        if (notification.read("content.sourceAccountCode").equals(sourceAccountCode) &&
            notification.read("content.destinationAccountCode").equals(destAccountCode)) {
            return notification;
        }
        return null;
    }

    public boolean endpointHasANotification(String endpoint) {
        return !"[]".equals(getResponseBody(endpoint).asString());
    }

    private ResponseBody getResponseBody(String endpoint) {
        ResponseBody body = RestAssured.get(endpoint).getBody();

        try {
            body.jsonPath().get("error");
        } catch (Exception e) {
            log.info("\n-------------------------------------------------------");
            log.info("\nget body response was: \n{}", body.prettyPrint());
            log.info("\n-------------------------------------------------------");
        }

        if (body.jsonPath().get("error").equals("Bin not found")) {
            throw new IllegalStateException(String.format("Bin no longer exists. Endpoint is showing: [%s]", body.jsonPath().get("error").toString()));
        }
        return body;
    }
}
