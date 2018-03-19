package com.adyen.mirakl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Adyen Mirakl Connector.
 * <p>
 * Properties are configured in the application.yml file. See {@link io.github.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {
    private String miraklPullCron;
    private String emailRetryCron;
    private String removeSentEmailsCron;
    private Integer initialDeltaDaysBack;

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

    public Integer getInitialDeltaDaysBack() {
        return initialDeltaDaysBack;
    }

    public void setInitialDeltaDaysBack(Integer initialDeltaDaysBack) {
        this.initialDeltaDaysBack = initialDeltaDaysBack;
    }
}
