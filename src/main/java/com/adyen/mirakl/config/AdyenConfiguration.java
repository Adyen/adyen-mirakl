package com.adyen.mirakl.config;

import com.adyen.Client;
import com.adyen.Config;
import com.adyen.enums.Environment;
import com.adyen.service.Account;
import com.adyen.service.Notification;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "adyenConfig", ignoreUnknownFields = false)
public class AdyenConfiguration {

    private String userName;
    private String password;
    private Environment environment;
    private String appName;
    private String endpoint;

    public String getUserName() {
        return userName;
    }

    public void setUserName(final String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(final Environment environment) {
        this.environment = environment;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(final String appName) {
        this.appName = appName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(final String endpoint) {
        this.endpoint = endpoint;
    }

    @Bean
    public Config adyenConfig(){
        final Config config = new Config();
        config.setUsername(userName);
        config.setPassword(password);
        config.setEnvironment(environment);
        config.setApplicationName(appName);
        config.setMarketPayEndpoint(endpoint);
        return config;
    }

    @Bean
    public Client adyenClient(){
        return new Client(adyenConfig());
    }

    @Bean
    public Notification adyenNotification(){
        return new Notification(adyenClient());
    }

    @Bean
    public Account createAccountService(){
        return new Account(adyenClient());
    }
}
