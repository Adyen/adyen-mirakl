package com.adyen.mirakl.config;

import com.google.common.collect.ImmutableMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Properties specific to Adyen Mirakl Connector.
 * <p>
 * Properties are configured in the application.yml file. See {@link io.github.jhipster.config.JHipsterProperties} for a good example.
 */
@Configuration
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {
    private String miraklPullCron;
    private String emailRetryCron;
    private String removeSentEmailsCron;
    private String payoutRetryCron;
    private String retryDocsCron;
    private Integer initialDeltaDaysBack;
    private Integer maxPayoutFailed;
    private Map<String, String> houseNumbersRegex;
    private String basicUsername;
    private String basicPassword;

    @Bean
    public Map<String, Pattern> houseNumberPatterns(){
        final ImmutableMap.Builder<String, Pattern> builder = ImmutableMap.builder();
        houseNumbersRegex.forEach((k, v) -> builder.put(k, Pattern.compile(v)));
        return builder.build();
    }

    public String getMiraklPullCron() {
        return miraklPullCron;
    }

    public void setMiraklPullCron(final String miraklPullCron) {
        this.miraklPullCron = miraklPullCron;
    }

    public String getEmailRetryCron() {
        return emailRetryCron;
    }

    public void setEmailRetryCron(final String emailRetryCron) {
        this.emailRetryCron = emailRetryCron;
    }

    public String getRemoveSentEmailsCron() {
        return removeSentEmailsCron;
    }

    public void setRemoveSentEmailsCron(final String removeSentEmailsCron) {
        this.removeSentEmailsCron = removeSentEmailsCron;
    }

    public String getPayoutRetryCron() {
        return payoutRetryCron;
    }

    public void setPayoutRetryCron(String payoutRetryCron) {
        this.payoutRetryCron = payoutRetryCron;
    }

    public Integer getInitialDeltaDaysBack() {
        return initialDeltaDaysBack;
    }

    public void setInitialDeltaDaysBack(Integer initialDeltaDaysBack) {
        this.initialDeltaDaysBack = initialDeltaDaysBack;
    }

    public Integer getMaxPayoutFailed() {
        return maxPayoutFailed;
    }

    public void setMaxPayoutFailed(Integer maxPayoutFailed) {
        this.maxPayoutFailed = maxPayoutFailed;
    }

    public Map<String, String> getHouseNumbersRegex() {
        return houseNumbersRegex;
    }

    public void setHouseNumbersRegex(final Map<String, String> houseNumbersRegex) {
        this.houseNumbersRegex = houseNumbersRegex;
    }

    public String getBasicUsername() {
        return basicUsername;
    }

    public void setBasicUsername(String basicUsername) {
        this.basicUsername = basicUsername;
    }

    public String getBasicPassword() {
        return basicPassword;
    }

    public void setBasicPassword(String basicPassword) {
        this.basicPassword = basicPassword;
    }

    public String getRetryDocsCron() {
        return retryDocsCron;
    }

    public void setRetryDocsCron(final String retryDocsCron) {
        this.retryDocsCron = retryDocsCron;
    }
}
