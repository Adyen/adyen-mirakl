package com.adyen.mirakl.cucumber.stepdefs.helpers;

import com.jayway.jsonpath.DocumentContext;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class World {

    public MiraklShop miraklShop;
    public DocumentContext notificationResponse;
    public List<DocumentContext> notifications;
}
