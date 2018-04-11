package com.adyen.mirakl.service;


import java.net.URL;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import com.adyen.Util.Util;
import com.adyen.mirakl.AdyenMiraklConnectorApp;
import com.adyen.mirakl.domain.AdyenPayoutError;
import com.adyen.mirakl.domain.MiraklVoucherEntry;
import com.adyen.mirakl.repository.AdyenPayoutErrorRepository;
import com.adyen.mirakl.repository.MiraklVoucherEntryRepository;
import com.adyen.model.Amount;
import com.adyen.model.marketpay.PayoutAccountHolderRequest;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import static org.junit.Assert.assertEquals;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = AdyenMiraklConnectorApp.class)
@Transactional
public class PayoutServiceIntegrationTest {

    @Autowired
    private PayoutService payoutService;

    @Autowired
    private AdyenPayoutErrorRepository adyenPayoutErrorRepository;

    @Autowired
    private MiraklVoucherEntryRepository miraklVoucherEntryRepository;

    @Before
    public void removeExistingTestAdyenPayoutErrors() {
        final List<AdyenPayoutError> adyenPayoutErrors = adyenPayoutErrorRepository.findAll();
        adyenPayoutErrorRepository.delete(adyenPayoutErrors);
        adyenPayoutErrorRepository.flush();

        final List<MiraklVoucherEntry> miraklVoucherEntries = miraklVoucherEntryRepository.findAll();
        miraklVoucherEntryRepository.delete(miraklVoucherEntries);
        miraklVoucherEntryRepository.flush();
    }

    @Test
    public void testStoreAccountHolderRequest() {
        PayoutAccountHolderRequest payoutAccountHolderRequest = new PayoutAccountHolderRequest();
        payoutAccountHolderRequest.setAccountCode("accountCode");
        payoutAccountHolderRequest.setBankAccountUUID("bankAccountUUID");
        payoutAccountHolderRequest.setAccountHolderCode("accountHolderCode");
        payoutAccountHolderRequest.setDescription("description");
        Amount adyenAmount = Util.createAmount("100", "EUR");
        payoutAccountHolderRequest.setAmount(adyenAmount);

        payoutService.storeAdyenPayoutError(payoutAccountHolderRequest, null, null);

        // check if it stores it properly
        final List<AdyenPayoutError> all = adyenPayoutErrorRepository.findAll();
        Assertions.assertThat(all.get(0).getRawRequest()).isEqualTo(PayoutService.GSON.toJson(payoutAccountHolderRequest));
    }

    @Test
    public void testParseMiraklCsv() throws Exception {
        URL url = Resources.getResource("paymentvouchers/PaymentVoucher_PayoutShop01.csv");
        final String csvFile = Resources.toString(url, Charsets.UTF_8);
        payoutService.parseMiraklCsv(csvFile);

        List<MiraklVoucherEntry> all = miraklVoucherEntryRepository.findAll();
        assertEquals(1, all.size());
        MiraklVoucherEntry miraklVoucherEntry = all.get(0);
        assertEquals("$shopId$", miraklVoucherEntry.getShopId());
        assertEquals("PayoutShop01", miraklVoucherEntry.getShopName());
        assertEquals("29.14", miraklVoucherEntry.getTransferAmount());
        assertEquals("0", miraklVoucherEntry.getSubscriptionAmount());
        assertEquals("EUR", miraklVoucherEntry.getCurrencyIsoCode());
        assertEquals("230207", miraklVoucherEntry.getInvoiceNumber());
        assertEquals("GB26TEST40051512347366", miraklVoucherEntry.getIban());
    }
}
