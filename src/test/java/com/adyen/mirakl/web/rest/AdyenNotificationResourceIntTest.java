package com.adyen.mirakl.web.rest;

import java.net.URL;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import com.adyen.mirakl.AdyenMiraklConnectorApp;
import com.adyen.mirakl.domain.AdyenNotification;
import com.adyen.mirakl.repository.AdyenNotificationRepository;
import com.adyen.mirakl.web.rest.errors.ExceptionTranslator;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import static com.adyen.mirakl.web.rest.TestUtil.createFormattingConversionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class for the AdyenNotificationResource REST controller.
 *
 * @see AdyenNotificationResource
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = AdyenMiraklConnectorApp.class)
public class AdyenNotificationResourceIntTest {

    @Autowired
    private AdyenNotificationRepository adyenNotificationRepository;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Mock
    private ApplicationEventPublisher publisherMock;

    private MockMvc restAdyenNotificationMockMvc;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final AdyenNotificationResource adyenNotificationResource = new AdyenNotificationResource(adyenNotificationRepository, publisherMock);
        this.restAdyenNotificationMockMvc = MockMvcBuilders.standaloneSetup(adyenNotificationResource)
                                                           .addFilter(springSecurityFilterChain)
                                                           .setCustomArgumentResolvers(pageableArgumentResolver)
                                                           .setControllerAdvice(exceptionTranslator)
                                                           .setConversionService(createFormattingConversionService())
                                                           .setMessageConverters(jacksonMessageConverter)
                                                           .build();
    }

    @Test
    @Transactional
    public void createAdyenNotification() throws Exception {
        URL url = Resources.getResource("adyenRequests/adyenRequestExample.json");
        final String adyenRequestJson = Resources.toString(url, Charsets.UTF_8);

        int databaseSizeBeforeCreate = adyenNotificationRepository.findAll().size();

        // Create the AdyenNotification - use test:test/dGVzdDp0ZXN0 as credentials
        restAdyenNotificationMockMvc.perform(post("/api/adyen-notifications").header("Authorization", "Basic dGVzdDp0ZXN0")
                                                                             .contentType(TestUtil.APPLICATION_JSON_UTF8)
                                                                             .content(TestUtil.convertObjectToJsonBytes(adyenRequestJson))).andExpect(status().isCreated());

        // Validate the AdyenNotification in the database
        List<AdyenNotification> adyenNotificationList = adyenNotificationRepository.findAll();
        assertThat(adyenNotificationList).hasSize(databaseSizeBeforeCreate + 1);
        AdyenNotification testAdyenNotification = adyenNotificationList.get(adyenNotificationList.size() - 1);
        assertThat(testAdyenNotification.getRawAdyenNotification()).isEqualTo(adyenRequestJson);
        assertThat(testAdyenNotification.isProcessed()).isFalse();

        verify(publisherMock).publishEvent(any());
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(AdyenNotification.class);
        AdyenNotification adyenNotification1 = new AdyenNotification();
        adyenNotification1.setId(1L);
        AdyenNotification adyenNotification2 = new AdyenNotification();
        adyenNotification2.setId(adyenNotification1.getId());
        assertThat(adyenNotification1).isEqualTo(adyenNotification2);
        adyenNotification2.setId(2L);
        assertThat(adyenNotification1).isNotEqualTo(adyenNotification2);
        adyenNotification1.setId(null);
        assertThat(adyenNotification1).isNotEqualTo(adyenNotification2);
    }
}
