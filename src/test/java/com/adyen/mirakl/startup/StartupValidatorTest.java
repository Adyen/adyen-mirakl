package com.adyen.mirakl.startup;

import com.google.common.collect.ImmutableList;
import com.mirakl.client.mmp.domain.additionalfield.MiraklFrontOperatorAdditionalField;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.event.ContextRefreshedEvent;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StartupValidatorTest {

    @InjectMocks
    private StartupValidator testObj;

    @Mock
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClientMock;
    @Mock
    private ContextRefreshedEvent eventMock;
    @Mock
    private MiraklFrontOperatorAdditionalField miraklFrontOperatorAdditionalFieldMock1, miraklFrontOperatorAdditionalFieldMock2, miraklFrontOperatorAdditionalFieldMock3;

    @Rule
    public ExpectedException thrown = ExpectedException.none();


    @Test
    public void startupSuccess() {
        when(miraklMarketplacePlatformOperatorApiClientMock.getAdditionalFields(any(com.mirakl.client.mmp.operator.request.additionalfield.MiraklGetAdditionalFieldRequest.class))).thenReturn(ImmutableList.of(miraklFrontOperatorAdditionalFieldMock1, miraklFrontOperatorAdditionalFieldMock2, miraklFrontOperatorAdditionalFieldMock3));
        when(miraklFrontOperatorAdditionalFieldMock1.getCode()).thenReturn("nonValidCode");
        when(miraklFrontOperatorAdditionalFieldMock2.getCode()).thenReturn(StartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE.toString());
        when(miraklFrontOperatorAdditionalFieldMock3.getCode()).thenReturn(StartupValidator.CustomMiraklFields.ADYEN_BANK_COUNTRY.toString());
        testObj.onApplicationEvent(eventMock);
    }

    @Test
    public void startupFail() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Startup validation failed, unable to find custom field: [adyen-legal-entity-type]");

        when(miraklMarketplacePlatformOperatorApiClientMock.getAdditionalFields(any(com.mirakl.client.mmp.operator.request.additionalfield.MiraklGetAdditionalFieldRequest.class))).thenReturn(ImmutableList.of(miraklFrontOperatorAdditionalFieldMock1, miraklFrontOperatorAdditionalFieldMock2));
        when(miraklFrontOperatorAdditionalFieldMock1.getCode()).thenReturn("nonValidCode");
        when(miraklFrontOperatorAdditionalFieldMock2.getCode()).thenReturn("anotherNonValidCode");
        when(miraklFrontOperatorAdditionalFieldMock3.getCode()).thenReturn("aotherAnotherNonValidCode");

        testObj.onApplicationEvent(eventMock);
    }

}
