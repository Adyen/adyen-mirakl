package com.adyen.mirakl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.mirakl.client.core.security.MiraklCredential;
import com.mirakl.client.mmp.front.core.MiraklMarketplacePlatformFrontApiClient;

@Configuration
@ConfigurationProperties(prefix = "miraklFront", ignoreUnknownFields = false)
public class MiraklFrontApiClientFactory extends MiraklProperties {

    @Bean
    public MiraklCredential createMiraklFrontCredential() {
        return new MiraklCredential(getMiraklApiKey());
    }

    @Bean
    public MiraklMarketplacePlatformFrontApiClient createMiraklMarketplacePlatformFrontApiClient() {
        return new MiraklMarketplacePlatformFrontApiClient(getMiraklEnvUrl(), createMiraklFrontCredential());
    }

}
