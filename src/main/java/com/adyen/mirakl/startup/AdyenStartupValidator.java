/*
 *                       ######
 *                       ######
 * ############    ####( ######  #####. ######  ############   ############
 * #############  #####( ######  #####. ######  #############  #############
 *        ######  #####( ######  #####. ######  #####  ######  #####  ######
 * ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 * ###### ######  #####( ######  #####. ######  #####          #####  ######
 * #############  #############  #############  #############  #####  ######
 *  ############   ############  #############   ############  #####  ######
 *                                      ######
 *                               #############
 *                               ############
 *
 * Adyen Mirakl Connector
 *
 * Copyright (c) 2018 Adyen B.V.
 * This file is open source and available under the MIT license.
 * See the LICENSE file for more info.
 *
 */

package com.adyen.mirakl.startup;

import com.adyen.model.marketpay.notification.*;
import com.adyen.service.Notification;
import com.adyen.service.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties("adyenNotificationsConfig")
public class AdyenStartupValidator implements ApplicationListener<ContextRefreshedEvent> {

    private final Logger log = LoggerFactory.getLogger(AdyenStartupValidator.class);

    @Resource
    private Notification adyenNotification;

    private List<NotificationConfigurationDetails> notificationConfigurationDetails;

    @Override
    public void onApplicationEvent(final ContextRefreshedEvent event) {
        try {
            //should not have logic in getters/setters
            //workaround as the eventConfigsContainer is not set during startup config mapping
            //application.yml does not force the setter, but tries the getter first
            //NotificationConfigurationDetails.setEventConfigs has logic in to map fields to eventConfigsContainer
            //but is not invoked as the NotificationConfigurationDetails.getEventConfigs creates a new list which we can add to
            notificationConfigurationDetails.forEach(x -> x.setEventConfigs(x.getEventConfigs()));
            sync();
        } catch (ApiException e) {
            if (e.getError() == null && e.getStatusCode() == 401) {
                // The ApiException does not have an error message in case of HTTP status code 401.
                // As this should only occur in case of misconfiguration (e.g., invalid credentials), let's be more explicit in the newly thrown exception.
                throw new IllegalStateException("Unable to sync Adyen notification configuration: Caught HTTP error 401. Are your Adyen MarketPlace credentials correct?", e);
            } else {
                throw new IllegalStateException(String.format("Unable to sync Adyen notification configuration: %s (HTTP status code = %d)", e.getError(), e.getStatusCode()), e);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sync Adyen notification configuration", e);
        }
    }

    private void sync() throws Exception {
        //map with notifyUrls as unique field
        final Map<String, NotificationConfigurationDetails> currentNotificationSetup = notificationConfigurationDetails.stream()
            .collect(Collectors.toMap(NotificationConfigurationDetails::getNotifyURL, Function.identity()));

        //filter for notification ids we care about
        final GetNotificationConfigurationListResponse notificationConfigurationList = adyenNotification.getNotificationConfigurationList();
        final Map<String, Long> notifyUrlsToIds = notificationConfigurationList.getConfigurations().stream()
            .filter(configurationDetail -> currentNotificationSetup.containsKey(configurationDetail.getNotifyURL()))
            .collect(Collectors.toMap(NotificationConfigurationDetails::getNotifyURL, NotificationConfigurationDetails::getNotificationId));

        //transfer the notification ID
        notifyUrlsToIds.forEach((url, id) -> currentNotificationSetup.get(url).setNotificationId(id));

        //separate update from create
        final Map<Boolean, List<NotificationConfigurationDetails>> toCreate = currentNotificationSetup.values().stream()
            .collect(Collectors.partitioningBy(detailsPredicate -> Objects.isNull(detailsPredicate.getNotificationId())));

        //create with our config
        for (NotificationConfigurationDetails configurationDetails : toCreate.get(true)) {
            final CreateNotificationConfigurationRequest createNotificationConfigurationRequest = new CreateNotificationConfigurationRequest();
            createNotificationConfigurationRequest.setConfigurationDetails(configurationDetails);
            final CreateNotificationConfigurationResponse createNotificationConfigurationResponse = adyenNotification.createNotificationConfiguration(createNotificationConfigurationRequest);
            log.info(String.format("Create notification. Psp ref: [%s]",  createNotificationConfigurationResponse.getPspReference()));
        }

        //update with our config
        for (NotificationConfigurationDetails configurationDetails : toCreate.get(false)) {
            final UpdateNotificationConfigurationRequest updateNotificationConfigurationRequest = new UpdateNotificationConfigurationRequest();
            updateNotificationConfigurationRequest.setConfigurationDetails(configurationDetails);
            final UpdateNotificationConfigurationResponse updateNotificationConfigurationResponse = adyenNotification.updateNotificationConfiguration(updateNotificationConfigurationRequest);
            log.info(String.format("Update notification. Psp ref: [%s]",  updateNotificationConfigurationResponse.getPspReference()));
        }
    }


    public Notification getAdyenNotification() {
        return adyenNotification;
    }

    public void setAdyenNotification(final Notification adyenNotification) {
        this.adyenNotification = adyenNotification;
    }

    public List<NotificationConfigurationDetails> getNotificationConfigurationDetails() {
        return notificationConfigurationDetails;
    }

    public void setNotificationConfigurationDetails(final List<NotificationConfigurationDetails> notificationConfigurationDetails) {
        this.notificationConfigurationDetails = notificationConfigurationDetails;
    }
}
