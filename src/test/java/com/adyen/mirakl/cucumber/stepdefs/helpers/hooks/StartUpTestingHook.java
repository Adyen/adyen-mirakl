package com.adyen.mirakl.cucumber.stepdefs.helpers.hooks;

import com.adyen.mirakl.domain.MiraklDelta;
import com.adyen.model.marketpay.notification.*;
import com.adyen.service.Notification;
import com.google.common.collect.ImmutableList;
import io.restassured.RestAssured;
import io.restassured.response.ResponseBody;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

@Component
@ConfigurationProperties(prefix = "requestbin", ignoreUnknownFields = false)
public class StartUpTestingHook implements ApplicationListener<ContextRefreshedEvent> {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Resource
    private TestHooks testHooks;
    @Resource
    private Notification adyenNotification;
    private String baseRequestbinUrl;
    private String baseRequestBinUrlPath;
    private Long notificationId;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        createDelta();
        createRequestBin();
        createNotificationConfiguration();
    }

    private void createDelta() {
        final MiraklDelta miraklDelta = new MiraklDelta();
        miraklDelta.setShopDelta(ZonedDateTime.now());
    }

    private void createNotificationConfiguration() {
        try {
            CreateNotificationConfigurationResponse configs = createConfigs();
            notificationId = configs.getConfigurationDetails().getNotificationId();
            log.info(String.format("Notification created successfully. notificationId: [%s]", configs.getConfigurationDetails().getNotificationId().toString()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not create config", e);
        }
    }

    private void createRequestBin() {
        ResponseBody body = RestAssured.post(baseRequestbinUrl.concat("api/v1/bins")).thenReturn().body();
        baseRequestBinUrlPath = baseRequestbinUrl.concat("api/v1/bins/").concat(body.jsonPath().get("name").toString()).concat("/requests");
        baseRequestbinUrl = baseRequestbinUrl.concat(body.jsonPath().get("name").toString());

        log.info(String.format("Requestbin-endpoint [%s]", baseRequestBinUrlPath));
    }

    private CreateNotificationConfigurationResponse createConfigs() throws Exception {
        CreateNotificationConfigurationRequest createNotificationConfigurationRequest = new CreateNotificationConfigurationRequest();
        NotificationConfigurationDetails configurationDetails = new NotificationConfigurationDetails();
        configurationDetails.setActive(true);
        configurationDetails.description(baseRequestbinUrl);
        // Event Configs
        configurationDetails.setEventConfigs(ImmutableList.of(
            notificationEventConfiguration(
                NotificationEventConfiguration.EventTypeEnum.ACCOUNT_HOLDER_CREATED,
                NotificationEventConfiguration.IncludeModeEnum.INCLUDE
            ),
            notificationEventConfiguration(
                NotificationEventConfiguration.EventTypeEnum.ACCOUNT_HOLDER_VERIFICATION,
                NotificationEventConfiguration.IncludeModeEnum.INCLUDE
            ),
            notificationEventConfiguration(
                NotificationEventConfiguration.EventTypeEnum.ACCOUNT_HOLDER_UPDATED,
                NotificationEventConfiguration.IncludeModeEnum.INCLUDE
            ),
            notificationEventConfiguration(
                NotificationEventConfiguration.EventTypeEnum.ACCOUNT_HOLDER_PAYOUT,
                NotificationEventConfiguration.IncludeModeEnum.INCLUDE
            ),
            notificationEventConfiguration(
                NotificationEventConfiguration.EventTypeEnum.ACCOUNT_HOLDER_STATUS_CHANGE,
                NotificationEventConfiguration.IncludeModeEnum.INCLUDE
            )
        ));

        configurationDetails.messageFormat(NotificationConfigurationDetails.MessageFormatEnum.JSON);
        configurationDetails.setNotifyURL(baseRequestbinUrl);
        configurationDetails.setSendActionHeader(true);
        configurationDetails.setSslProtocol(NotificationConfigurationDetails.SslProtocolEnum.SSL);
        createNotificationConfigurationRequest.setConfigurationDetails(configurationDetails);
        CreateNotificationConfigurationResponse notificationConfiguration = adyenNotification.createNotificationConfiguration(createNotificationConfigurationRequest);

        Awaitility.with()
            .pollDelay(Duration.ONE_MINUTE)
            .await().atMost(new Duration(110,TimeUnit.SECONDS))
            .untilAsserted(() -> {
            final GetNotificationConfigurationListResponse all = adyenNotification.getNotificationConfigurationList();
            final boolean found = all.getConfigurations().stream().anyMatch(x -> configurationDetails.getNotifyURL().equals(x.getNotifyURL()));
            Assertions.assertThat(found).isTrue();
        });

        testHooks.setConfigurationDetails(configurationDetails);
        return notificationConfiguration;
    }

    protected NotificationEventConfiguration notificationEventConfiguration(NotificationEventConfiguration.EventTypeEnum a, NotificationEventConfiguration.IncludeModeEnum b) {
        NotificationEventConfiguration notificationEventConfiguration = new NotificationEventConfiguration();
        notificationEventConfiguration.setEventType(a);
        notificationEventConfiguration.setIncludeMode(b);
        return notificationEventConfiguration;
    }

    public TestHooks getTestHooks() {
        return testHooks;
    }

    public void setTestHooks(TestHooks testHooks) {
        this.testHooks = testHooks;
    }

    public Notification getAdyenNotification() {
        return adyenNotification;
    }

    public void setAdyenNotification(Notification adyenNotification) {
        this.adyenNotification = adyenNotification;
    }

    public String getBaseRequestbinUrl() {
        return baseRequestbinUrl;
    }

    public void setBaseRequestbinUrl(String baseRequestbinUrl) {
        this.baseRequestbinUrl = baseRequestbinUrl;
    }

    public String getBaseRequestBinUrlPath() {
        return baseRequestBinUrlPath;
    }

    public void setBaseRequestBinUrlPath(String baseRequestBinUrlPath) {
        this.baseRequestBinUrlPath = baseRequestBinUrlPath;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(final Long notificationId) {
        this.notificationId = notificationId;
    }
}
