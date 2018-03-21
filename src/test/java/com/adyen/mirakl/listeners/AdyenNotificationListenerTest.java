package com.adyen.mirakl.listeners;

import com.adyen.mirakl.config.MailTemplateService;
import com.adyen.mirakl.domain.AdyenNotification;
import com.adyen.mirakl.events.AdyenNotifcationEvent;
import com.adyen.mirakl.repository.AdyenNotificationRepository;
import com.adyen.model.marketpay.GetAccountHolderRequest;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.ShareholderContact;
import com.adyen.notification.NotificationHandler;
import com.adyen.service.Account;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AdyenNotificationListenerTest {

    private AdyenNotificationListener adyenNotificationListener;

    @Mock
    private AdyenNotificationRepository adyenNotificationRepositoryMock;
    @Mock
    private AdyenNotifcationEvent eventMock;
    @Mock
    private AdyenNotification adyenNotificationMock;
    @Mock
    private MailTemplateService mailTemplateServiceMock;
    @Mock
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClient;
    @Mock
    private MiraklShop miraklShopMock;
    @Mock
    private MiraklShops miraklShopsMock;
    @Mock
    private Account adyenAccountServiceMock;
    @Mock
    private ShareholderContact shareholderMock1, shareholderMock2;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private GetAccountHolderResponse getAccountHolderResponseMock;
    @Captor
    private ArgumentCaptor<MiraklGetShopsRequest> miraklShopsRequestCaptor;
    @Captor
    private ArgumentCaptor<GetAccountHolderRequest> accountHolderRequestCaptor;

    @Before
    public void setup(){
        adyenNotificationListener = new AdyenNotificationListener(new NotificationHandler(), adyenNotificationRepositoryMock, mailTemplateServiceMock, miraklMarketplacePlatformOperatorApiClient, adyenAccountServiceMock);
        when(eventMock.getDbId()).thenReturn(1L);
        when(adyenNotificationRepositoryMock.findOneById(1L)).thenReturn(adyenNotificationMock);
    }

    @Test
    public void sendEmail() throws IOException {
        URL url = Resources.getResource("adyenRequests/BANK_ACCOUNT_VERIFICATION-RETRY_LIMIT_REACHED.json");
        final String adyenRequestJson = Resources.toString(url, Charsets.UTF_8);
        when(adyenNotificationMock.getRawAdyenNotification()).thenReturn(adyenRequestJson);

        when(miraklMarketplacePlatformOperatorApiClient.getShops(miraklShopsRequestCaptor.capture())).thenReturn(miraklShopsMock);
        when(miraklShopsMock.getShops()).thenReturn(ImmutableList.of(miraklShopMock));

        adyenNotificationListener.handleContextRefresh(eventMock);

        final MiraklGetShopsRequest miraklGetShopRequest = miraklShopsRequestCaptor.getValue();
        Assertions.assertThat(miraklGetShopRequest.getShopIds()).containsOnly("2146");
        verify(mailTemplateServiceMock).sendMiraklShopEmailFromTemplate(miraklShopMock, Locale.ENGLISH, "bankAccountVerificationEmail", "email.bank.verification.title");
        verify(adyenNotificationRepositoryMock).delete(1L);
    }


    @Test
    public void shouldSendEmailForIdentityVerificationAwaitingData() throws Exception {
        URL url = Resources.getResource("adyenRequests/ACCOUNT_HOLDER_VERIFICATION_AWAITING_DATA.json");
        final String adyenRequestJson = Resources.toString(url, Charsets.UTF_8);
        when(adyenNotificationMock.getRawAdyenNotification()).thenReturn(adyenRequestJson);
        when(adyenAccountServiceMock.getAccountHolder(accountHolderRequestCaptor.capture())).thenReturn(getAccountHolderResponseMock);
        when(getAccountHolderResponseMock.getAccountHolderDetails().getBusinessDetails().getShareholders()).thenReturn(ImmutableList.of(shareholderMock1, shareholderMock2));
        when(shareholderMock1.getShareholderCode()).thenReturn("invalidShareholderCode");
        when(shareholderMock2.getShareholderCode()).thenReturn("24610d08-9d80-4a93-85f3-78d475274e08");

        adyenNotificationListener.handleContextRefresh(eventMock);

        final GetAccountHolderRequest requestCaptorValue = accountHolderRequestCaptor.getValue();
        Assertions.assertThat(requestCaptorValue.getAccountHolderCode()).isEqualTo("8255");
        verify(mailTemplateServiceMock).sendShareholderEmailFromTemplate(shareholderMock2, "8255", Locale.ENGLISH, "accountHolderAwaitingDataEmail", "email.account.verification.awaiting.data.title");
        verify(adyenNotificationRepositoryMock).delete(1L);
    }



}
