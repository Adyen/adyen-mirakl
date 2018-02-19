package com.adyen.mirakl.startup;

import com.mirakl.client.mmp.domain.additionalfield.AbstractMiraklAdditionalField;
import com.mirakl.client.mmp.domain.additionalfield.MiraklFrontOperatorAdditionalField;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.operator.request.additionalfield.MiraklGetAdditionalFieldRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class StartupValidator implements ApplicationListener<ContextRefreshedEvent> {

    private final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    public enum CustomMiraklFields {
        ADYEN_LEGAL_ENTITY_TYPE("adyen-legal-entity-type"),
        ADYEN_BANK_COUNTRY("adyen-bank-country");

        private final String name;

        CustomMiraklFields(String s) {
            name = s;
        }

        public String toString() {
            return this.name;
        }
    }

    public enum AdyenLegalEntityType {
        INDIVIDUAL("INDIVIDUAL"),
        BUSINESS("BUSINESS");

        private final String name;

        AdyenLegalEntityType(String s) {
            name = s;
        }

        public String toString() {
            return this.name;
        }
    }

    @Resource
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient;

    @Override
    public void onApplicationEvent(final ContextRefreshedEvent event) {
        MiraklGetAdditionalFieldRequest miraklGetAdditionalFieldRequest = new MiraklGetAdditionalFieldRequest();
        final List<MiraklFrontOperatorAdditionalField> additionalFields = miraklMarketplacePlatformOperatorApiClient.getAdditionalFields(miraklGetAdditionalFieldRequest);

        for (CustomMiraklFields customMiraklFields : CustomMiraklFields.values()) {
            boolean startupSuccess = additionalFields.stream()
                .map(AbstractMiraklAdditionalField::getCode)
                .anyMatch(customFieldName -> customMiraklFields.toString().equalsIgnoreCase(customFieldName));
            if (startupSuccess) {
                log.info(String.format("Startup validation succeeded, custom field found: [%s]", customMiraklFields.toString()));
            } else {
                throw new IllegalStateException(String.format("Startup validation failed, unable to find custom field: [%s]", customMiraklFields.toString()));
            }
        }
    }

}
