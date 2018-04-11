package com.adyen.mirakl.listeners;

import com.adyen.mirakl.service.DocService;
import com.adyen.mirakl.service.MailTemplateService;
import com.adyen.mirakl.domain.AdyenNotification;
import com.adyen.mirakl.events.AdyenNotifcationEvent;
import com.adyen.mirakl.repository.AdyenNotificationRepository;
import com.adyen.mirakl.service.RetryPayoutService;
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
    @Mock
    private RetryPayoutService retryPayoutService;
    @Mock
    private DocService docServiceMock;
    @Captor
    private ArgumentCaptor<MiraklGetShopsRequest> miraklShopsRequestCaptor;
    @Captor
    private ArgumentCaptor<GetAccountHolderRequest> accountHolderRequestCaptor;

    @Before
    public void setup(){
        adyenNotificationListener = new AdyenNotificationListener(new NotificationHandler(), adyenNotificationRepositoryMock, mailTemplateServiceMock, miraklMarketplacePlatformOperatorApiClient, adyenAccountServiceMock, retryPayoutService, docServiceMock);
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
        verify(mailTemplateServiceMock).sendMiraklShopEmailFromTemplate(miraklShopMock, Locale.getDefault(), "bankAccountVerificationEmail", "email.bank.verification.title");
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
        verify(mailTemplateServiceMock).sendShareholderEmailFromTemplate(shareholderMock2, "8255", Locale.getDefault(), "accountHolderAwaitingIdentityEmail", "email.account.verification.awaiting.id.title");
        verify(adyenNotificationRepositoryMock).delete(1L);
    }

    @Test
    public void shouldSendEmailForPassportVerificationAwaitingData() throws Exception {
        URL url = Resources.getResource("adyenRequests/PASSPORT_VERIFICATION_AWAITING_DATA.json");
        final String adyenRequestJson = Resources.toString(url, Charsets.UTF_8);
        when(adyenNotificationMock.getRawAdyenNotification()).thenReturn(adyenRequestJson);
        when(adyenAccountServiceMock.getAccountHolder(accountHolderRequestCaptor.capture())).thenReturn(getAccountHolderResponseMock);
        when(getAccountHolderResponseMock.getAccountHolderDetails().getBusinessDetails().getShareholders()).thenReturn(ImmutableList.of(shareholderMock1, shareholderMock2));
        when(shareholderMock1.getShareholderCode()).thenReturn("invalidShareholderCode");
        when(shareholderMock2.getShareholderCode()).thenReturn("24610d08-9d80-4a93-85f3-78d475274e08");

        adyenNotificationListener.handleContextRefresh(eventMock);

        final GetAccountHolderRequest requestCaptorValue = accountHolderRequestCaptor.getValue();
        Assertions.assertThat(requestCaptorValue.getAccountHolderCode()).isEqualTo("8255");
        verify(mailTemplateServiceMock).sendShareholderEmailFromTemplate(shareholderMock2, "8255", Locale.getDefault(), "accountHolderAwaitingPassportEmail", "email.account.verification.awaiting.passport.title");
        verify(adyenNotificationRepositoryMock).delete(1L);
    }

    @Test
    public void shouldSendEmailForPassportVerificationInvalidData() throws Exception {
        URL url = Resources.getResource("adyenRequests/PASSPORT_VERIFICATION_INVALID_DATA.json");
        final String adyenRequestJson = Resources.toString(url, Charsets.UTF_8);
        when(adyenNotificationMock.getRawAdyenNotification()).thenReturn(adyenRequestJson);
        when(adyenAccountServiceMock.getAccountHolder(accountHolderRequestCaptor.capture())).thenReturn(getAccountHolderResponseMock);
        when(getAccountHolderResponseMock.getAccountHolderDetails().getBusinessDetails().getShareholders()).thenReturn(ImmutableList.of(shareholderMock1, shareholderMock2));
        when(shareholderMock1.getShareholderCode()).thenReturn("invalidShareholderCode");
        when(shareholderMock2.getShareholderCode()).thenReturn("24610d08-9d80-4a93-85f3-78d475274e08");

        adyenNotificationListener.handleContextRefresh(eventMock);

        final GetAccountHolderRequest requestCaptorValue = accountHolderRequestCaptor.getValue();
        Assertions.assertThat(requestCaptorValue.getAccountHolderCode()).isEqualTo("8255");
        verify(mailTemplateServiceMock).sendShareholderEmailFromTemplate(shareholderMock2, "8255", Locale.getDefault(), "accountHolderInvalidPassportEmail", "email.account.verification.invalid.passport.title");
        verify(adyenNotificationRepositoryMock).delete(1L);
    }

    @Test
    public void shouldSendEmailForIdentityVerificationInvalidData() throws Exception {
        URL url = Resources.getResource("adyenRequests/IDENTITY_VERIFICATION_INVALID_DATA.json");
        final String adyenRequestJson = Resources.toString(url, Charsets.UTF_8);
        when(adyenNotificationMock.getRawAdyenNotification()).thenReturn(adyenRequestJson);
        when(adyenAccountServiceMock.getAccountHolder(accountHolderRequestCaptor.capture())).thenReturn(getAccountHolderResponseMock);
        when(getAccountHolderResponseMock.getAccountHolderDetails().getBusinessDetails().getShareholders()).thenReturn(ImmutableList.of(shareholderMock1, shareholderMock2));
        when(shareholderMock1.getShareholderCode()).thenReturn("invalidShareholderCode");
        when(shareholderMock2.getShareholderCode()).thenReturn("24610d08-9d80-4a93-85f3-78d475274e08");

        adyenNotificationListener.handleContextRefresh(eventMock);

        final GetAccountHolderRequest requestCaptorValue = accountHolderRequestCaptor.getValue();
        Assertions.assertThat(requestCaptorValue.getAccountHolderCode()).isEqualTo("8255");
        verify(mailTemplateServiceMock).sendShareholderEmailFromTemplate(shareholderMock2, "8255", Locale.getDefault(), "accountHolderInvalidIdentityEmail", "email.account.verification.invalid.id.title");
        verify(adyenNotificationRepositoryMock).delete(1L);
    }

    @Test
    public void shouldSendEmailForCompanyVerificationInvalidData() throws Exception {
        URL url = Resources.getResource("adyenRequests/COMPANY_VERIFICATION_INVALID_DATA.json");
        final String adyenRequestJson = Resources.toString(url, Charsets.UTF_8);
        when(adyenNotificationMock.getRawAdyenNotification()).thenReturn(adyenRequestJson);

        when(miraklMarketplacePlatformOperatorApiClient.getShops(miraklShopsRequestCaptor.capture())).thenReturn(miraklShopsMock);
        when(miraklShopsMock.getShops()).thenReturn(ImmutableList.of(miraklShopMock));

        adyenNotificationListener.handleContextRefresh(eventMock);

        final MiraklGetShopsRequest miraklGetShopRequest = miraklShopsRequestCaptor.getValue();
        Assertions.assertThat(miraklGetShopRequest.getShopIds()).containsOnly("8837");
        verify(mailTemplateServiceMock).sendMiraklShopEmailFromTemplate(miraklShopMock, Locale.getDefault(), "companyInvalidIdData", "email.company.verification.invalid.id.title");
        verify(adyenNotificationRepositoryMock).delete(1L);
    }

    @Test
    public void shouldSendEmailForCompanyVerificationAwaitingData() throws Exception {
        URL url = Resources.getResource("adyenRequests/COMPANY_VERIFICATION_AWAITING_DATA.json");
        final String adyenRequestJson = Resources.toString(url, Charsets.UTF_8);
        when(adyenNotificationMock.getRawAdyenNotification()).thenReturn(adyenRequestJson);

        when(miraklMarketplacePlatformOperatorApiClient.getShops(miraklShopsRequestCaptor.capture())).thenReturn(miraklShopsMock);
        when(miraklShopsMock.getShops()).thenReturn(ImmutableList.of(miraklShopMock));

        adyenNotificationListener.handleContextRefresh(eventMock);

        final MiraklGetShopsRequest miraklGetShopRequest = miraklShopsRequestCaptor.getValue();
        Assertions.assertThat(miraklGetShopRequest.getShopIds()).containsOnly("8837");
        verify(mailTemplateServiceMock).sendMiraklShopEmailFromTemplate(miraklShopMock, Locale.getDefault(), "companyAwaitingIdData", "email.company.verification.awaiting.id.title");
        verify(adyenNotificationRepositoryMock).delete(1L);
    }

    @Test
    public void shouldSendEmailForAllowPayout() throws Exception {
        URL url = Resources.getResource("adyenRequests/ACCOUNT_HOLDER_STATUS_CHANGE_ALLOW_PAYOUT.json");
        final String adyenRequestJson = Resources.toString(url, Charsets.UTF_8);
        when(adyenNotificationMock.getRawAdyenNotification()).thenReturn(adyenRequestJson);

        when(miraklMarketplacePlatformOperatorApiClient.getShops(miraklShopsRequestCaptor.capture())).thenReturn(miraklShopsMock);
        when(miraklShopsMock.getShops()).thenReturn(ImmutableList.of(miraklShopMock));

        adyenNotificationListener.handleContextRefresh(eventMock);

        final MiraklGetShopsRequest requestCaptorValue = miraklShopsRequestCaptor.getValue();
        Assertions.assertThat(requestCaptorValue.getShopIds()).containsOnly("8278");
        verify(mailTemplateServiceMock).sendMiraklShopEmailFromTemplate(miraklShopMock, Locale.getDefault(), "nowPayable", "email.account.status.now.true.title");
        verify(adyenNotificationRepositoryMock).delete(1L);
    }

    @Test
    public void shouldSendEmailForNotAllowedPayouts() throws Exception {
        URL url = Resources.getResource("adyenRequests/ACCOUNT_HOLDER_STATUS_CHANGE_NOT_ALLOW_PAYOUT.json");
        final String adyenRequestJson = Resources.toString(url, Charsets.UTF_8);
        when(adyenNotificationMock.getRawAdyenNotification()).thenReturn(adyenRequestJson);

        when(miraklMarketplacePlatformOperatorApiClient.getShops(miraklShopsRequestCaptor.capture())).thenReturn(miraklShopsMock);
        when(miraklShopsMock.getShops()).thenReturn(ImmutableList.of(miraklShopMock));

        adyenNotificationListener.handleContextRefresh(eventMock);

        final MiraklGetShopsRequest requestCaptorValue = miraklShopsRequestCaptor.getValue();
        Assertions.assertThat(requestCaptorValue.getShopIds()).containsOnly("8278");
        verify(mailTemplateServiceMock).sendMiraklShopEmailFromTemplate(miraklShopMock, Locale.getDefault(), "payoutRevoked", "email.account.status.now.false.title");
        verify(adyenNotificationRepositoryMock).delete(1L);
    }

    @Test
    public void shouldRemoveMiraklDocsWhenDataProvidedForShareholder() throws IOException {
        URL url = Resources.getResource("adyenRequests/COMPANY_VERIFICATION_DATA_PROVIDED.json");
        final String adyenRequestJson = Resources.toString(url, Charsets.UTF_8);
        when(adyenNotificationMock.getRawAdyenNotification()).thenReturn(adyenRequestJson);

        adyenNotificationListener.handleContextRefresh(eventMock);

        verify(docServiceMock).removeMiraklMediaForShareHolder("c6adfbe1-4794-4e31-9861-9f69dee3a60e");
    }

}
