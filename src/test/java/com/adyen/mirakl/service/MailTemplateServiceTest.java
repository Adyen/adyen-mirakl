package com.adyen.mirakl.service;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.adyen.mirakl.config.MiraklOperatorConfiguration;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thymeleaf.spring4.SpringTemplateEngine;
import com.adyen.mirakl.AdyenMiraklConnectorApp;
import com.adyen.model.marketpay.Message;
import com.google.common.io.Resources;
import com.mirakl.client.core.internal.mapper.CustomObjectMapper;
import com.mirakl.client.mmp.domain.shop.MiraklContactInformation;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import io.github.jhipster.config.JHipsterProperties;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AdyenMiraklConnectorApp.class)
public class MailTemplateServiceTest {

    private MailTemplateService mailTemplateService;

    @Mock
    private MailService mailServiceMock;

    @Autowired
    private JHipsterProperties jHipsterProperties;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private MiraklOperatorConfiguration miraklOperatorConfiguration;

    @Captor
    private ArgumentCaptor<String> contentCaptor;

    @Before
    public void setup() {
        mailTemplateService = new MailTemplateService(jHipsterProperties, mailServiceMock, templateEngine, messageSource, miraklOperatorConfiguration);
    }

    @Test
    public void testSendEmailFromTemplateNoUser() throws Exception {
        final URL url = Resources.getResource("miraklRequests/miraklShopsMock.json");
        final CustomObjectMapper objectMapper = new CustomObjectMapper();
        final MiraklShop miraklShop = objectMapper.readValue(url, MiraklShops.class).getShops().iterator().next();

        mailTemplateService.sendMiraklShopEmailFromTemplate(miraklShop, Locale.UK, "testMiraklShopEmail", "email.test.title");

        verify(mailServiceMock).sendEmail("adyen-mirakl-cb966314-55c3-40e6-91f7-db6d8f0be825@mailinator.com",
                                          "test title",
                                          "<html>test title, http://127.0.0.1:8080, Mr, Ford, TestData, null/mmp/shop/account/shop/5073</html>\n",
                                          false,
                                          true);
    }

    @Test
    public void testSendOperatorEmailPayoutFailure() {
        final MiraklShop miraklShop = new MiraklShop();
        final MiraklContactInformation miraklContactInformation = new MiraklContactInformation();
        miraklShop.setContactInformation(miraklContactInformation);

        miraklContactInformation.setCivility("Mr");
        miraklContactInformation.setFirstname("John");
        miraklContactInformation.setLastname("Doe");
        miraklContactInformation.setEmail("adyen-mirakl-cb966314-55c3-40e6-91f7-db6d8f0be825@mailinator.com");

        List<String> errors = new ArrayList<>();
        errors.add("Error 1");
        errors.add("Error 2");

        doNothing().when(mailServiceMock).sendEmail(isA(String.class), isA(String.class), contentCaptor.capture(), anyBoolean(), anyBoolean());

        mailTemplateService.sendSellerEmailWithErrors(miraklShop, errors);
        final String content = contentCaptor.getValue();

        // verify that all the error strings are there
        errors.forEach((e) -> Assertions.assertThat(content).contains(e));
    }

    @Test
    public void sendAccountHolderPayoutFailedEmail() {

        final MiraklShop miraklShop = new MiraklShop();
        final MiraklContactInformation miraklContactInformation = new MiraklContactInformation();
        miraklShop.setContactInformation(miraklContactInformation);

        miraklContactInformation.setCivility("Mr");
        miraklContactInformation.setFirstname("John");
        miraklContactInformation.setLastname("Doe");
        miraklContactInformation.setEmail("adyen-mirakl-cb966314-55c3-40e6-91f7-db6d8f0be825@mailinator.com");

        doNothing().when(mailServiceMock).sendEmail(isA(String.class), isA(String.class), contentCaptor.capture(), anyBoolean(), anyBoolean());

        final String code = "10_063";
        final String text = "Payout is not allowed because the account does not have the payout state.";

        Message message = new Message();
        message.setCode(code);
        message.setText(text);
        mailTemplateService.sendOperatorEmailPayoutFailure(miraklShop, message);

        final String content = contentCaptor.getValue();
        // verify that all the payout code and text are there
        Assertions.assertThat(content).contains(code);
        Assertions.assertThat(content).contains(text);
    }
}
