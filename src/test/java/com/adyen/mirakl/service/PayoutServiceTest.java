package com.adyen.mirakl.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import com.adyen.mirakl.domain.AdyenPayoutError;
import com.adyen.mirakl.domain.MiraklVoucherEntry;
import com.adyen.mirakl.repository.AdyenPayoutErrorRepository;
import com.adyen.model.marketpay.AccountHolderDetails;
import com.adyen.model.marketpay.BankAccountDetail;
import com.adyen.model.marketpay.GetAccountHolderRequest;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.PayoutAccountHolderRequest;
import com.adyen.model.marketpay.PayoutAccountHolderResponse;
import com.adyen.model.marketpay.TransferFundsRequest;
import com.adyen.service.Account;
import com.adyen.service.Fund;
import com.adyen.service.exception.ApiException;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PayoutServiceTest {

    @InjectMocks
    private PayoutService payoutService;

    @Mock
    private Account adyenAccountServiceMock;

    @Mock
    private Fund adyenFundServiceMock;

    @Mock
    private AdyenPayoutErrorRepository adyenPayoutErrorRepository;

    @Captor
    private ArgumentCaptor<GetAccountHolderRequest> accountHolderRequestCaptor;

    @Captor
    private ArgumentCaptor<TransferFundsRequest> transferFundsRequestArgumentCaptor;

    @Captor
    private ArgumentCaptor<PayoutAccountHolderRequest> payoutAccountHolderRequestCaptor;

    @Test
    public void testGetBankAccountUUID() {
        GetAccountHolderResponse getAccountHolderResponse = getResponseWithBankDetails();
        assertEquals("2a421c72-ead7-4ad3-8741-80a0aebb8758", payoutService.getBankAccountUUID(getAccountHolderResponse, "GB29NWBK60161331926819"));
    }

    @Test(expected = RuntimeException.class)
    public void testGetBankAccountUUIDException() {
        GetAccountHolderResponse getAccountHolderResponse = getResponseWithBankDetails();
        payoutService.getBankAccountUUID(getAccountHolderResponse, "invalidiban");
    }

    @Test
    public void testPayout() throws Exception {
        GetAccountHolderResponse getAccountHolderResponse = getResponseWithBankDetails();

        MiraklVoucherEntry miraklVoucherEntry = new MiraklVoucherEntry();
        miraklVoucherEntry.setShopId("shop-id");
        miraklVoucherEntry.setTransferAmount("10.25");
        miraklVoucherEntry.setCurrencyIsoCode("EUR");
        miraklVoucherEntry.setIban("GB29NWBK60161331926819");
        miraklVoucherEntry.setInvoiceNumber("1111");
        miraklVoucherEntry.setShopName("shop-name");

        PayoutAccountHolderRequest request = payoutService.createPayoutAccountHolderRequest(getAccountHolderResponse, miraklVoucherEntry);
        assertEquals("2a421c72-ead7-4ad3-8741-80a0aebb8758", request.getBankAccountUUID());
        assertEquals("2000", request.getAccountHolderCode());
        assertEquals("123456", request.getAccountCode());
        assertEquals(1025L, (long) request.getAmount().getValue());
        assertEquals("EUR", request.getAmount().getCurrency());
        assertEquals("Invoice number: 1111, Payout shop shop-name (2000)", request.getDescription());
        assertEquals("1111", request.getMerchantReference());
    }

    public GetAccountHolderResponse getResponseWithBankDetails() {
        BankAccountDetail bankAccountDetail = new BankAccountDetail();
        bankAccountDetail.setBankAccountUUID("7ea30ecb-8ec9-4012-9c6c-c1abf7c4f90a");
        bankAccountDetail.setIban("GB26MIDL40051512345674");
        BankAccountDetail bankAccountDetail1 = new BankAccountDetail();
        bankAccountDetail1.setBankAccountUUID("2a421c72-ead7-4ad3-8741-80a0aebb8758");
        bankAccountDetail1.setIban("GB29NWBK60161331926819");
        AccountHolderDetails accountHolderDetails = new AccountHolderDetails();
        accountHolderDetails.addBankAccountDetail(bankAccountDetail);
        accountHolderDetails.addBankAccountDetail(bankAccountDetail1);
        GetAccountHolderResponse getAccountHolderResponse = new GetAccountHolderResponse();
        getAccountHolderResponse.setAccountHolderDetails(accountHolderDetails);
        getAccountHolderResponse.setAccountHolderCode("2000");

        com.adyen.model.marketpay.Account marketpayAccount = new com.adyen.model.marketpay.Account();
        marketpayAccount.setAccountCode("123456");
        getAccountHolderResponse.addAccount(marketpayAccount);

        return getAccountHolderResponse;
    }

    @Test
    public void testCreateTransferFundsSubscription() throws Exception {
        GetAccountHolderResponse getAccountHolderResponse = getResponseWithBankDetails();
        MiraklVoucherEntry miraklVoucherEntry = new MiraklVoucherEntry();
        miraklVoucherEntry.setSubscriptionAmount("12.34");
        miraklVoucherEntry.setCurrencyIsoCode("EUR");
        TransferFundsRequest transferFundsRequest = payoutService.createTransferFundsSubscription(getAccountHolderResponse, miraklVoucherEntry);
        assertEquals(1234L, (long) transferFundsRequest.getAmount().getValue());
        assertEquals("EUR", transferFundsRequest.getAmount().getCurrency());
    }

    @Test
    public void testProcessMiraklVoucherEntry() throws Exception {
        GetAccountHolderResponse getResponseWithBankDetails = getResponseWithBankDetails();
        when(adyenAccountServiceMock.getAccountHolder(accountHolderRequestCaptor.capture())).thenReturn(getResponseWithBankDetails);

        PayoutAccountHolderResponse payoutAccountHolderResponse = new PayoutAccountHolderResponse();
        payoutAccountHolderResponse.setPspReference("pspReference");
        when(adyenFundServiceMock.payoutAccountHolder(payoutAccountHolderRequestCaptor.capture())).thenReturn(payoutAccountHolderResponse);
        when(adyenFundServiceMock.transferFunds(transferFundsRequestArgumentCaptor.capture())).thenReturn(null);

        MiraklVoucherEntry miraklVoucherEntry = new MiraklVoucherEntry();
        miraklVoucherEntry.setShopId("shop-id");
        miraklVoucherEntry.setTransferAmount("10.00");
        miraklVoucherEntry.setCurrencyIsoCode("EUR");
        miraklVoucherEntry.setIban("GB29NWBK60161331926819");
        miraklVoucherEntry.setInvoiceNumber("invoice-number");
        miraklVoucherEntry.setShopName("shop-name");
        miraklVoucherEntry.setSubscriptionAmount("0.00");

        payoutService.processMiraklVoucherEntry(miraklVoucherEntry);

        verify(adyenFundServiceMock, never()).transferFunds(any());

        PayoutAccountHolderRequest payoutAccountHolderRequest = payoutAccountHolderRequestCaptor.getValue();
        assertEquals(1000L, payoutAccountHolderRequest.getAmount().getValue().longValue());

        miraklVoucherEntry.setSubscriptionAmount("20.00");

        payoutService.processMiraklVoucherEntry(miraklVoucherEntry);
        payoutAccountHolderRequest = payoutAccountHolderRequestCaptor.getValue();
        assertEquals(1000L, payoutAccountHolderRequest.getAmount().getValue().longValue());

        TransferFundsRequest transferFundsRequest = transferFundsRequestArgumentCaptor.getValue();
        assertEquals(2000L, transferFundsRequest.getAmount().getValue().longValue());
    }

    @Test
    public void testProcessMiraklVoucherEntryWithError() throws Exception {
        GetAccountHolderResponse getResponseWithBankDetails = getResponseWithBankDetails();
        when(adyenAccountServiceMock.getAccountHolder(accountHolderRequestCaptor.capture())).thenReturn(getResponseWithBankDetails);
        when(adyenFundServiceMock.payoutAccountHolder(any())).thenThrow(new ApiException("error", 500));

        MiraklVoucherEntry miraklVoucherEntry = new MiraklVoucherEntry();
        miraklVoucherEntry.setIban("GB29NWBK60161331926819");
        miraklVoucherEntry.setTransferAmount("10.00");
        miraklVoucherEntry.setCurrencyIsoCode("EUR");
        payoutService.processMiraklVoucherEntry(miraklVoucherEntry);

        verify(adyenPayoutErrorRepository).save(isA(AdyenPayoutError.class));
    }
}
