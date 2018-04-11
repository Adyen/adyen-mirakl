package com.adyen.mirakl.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import javax.annotation.Resource;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.adyen.Util.Util;
import com.adyen.mirakl.domain.AdyenPayoutError;
import com.adyen.mirakl.domain.MiraklVoucherEntry;
import com.adyen.mirakl.repository.AdyenPayoutErrorRepository;
import com.adyen.mirakl.repository.MiraklVoucherEntryRepository;
import com.adyen.model.Amount;
import com.adyen.model.marketpay.BankAccountDetail;
import com.adyen.model.marketpay.GetAccountHolderRequest;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.PayoutAccountHolderRequest;
import com.adyen.model.marketpay.PayoutAccountHolderResponse;
import com.adyen.model.marketpay.TransferFundsRequest;
import com.adyen.model.marketpay.TransferFundsResponse;
import com.adyen.service.Account;
import com.adyen.service.Fund;
import com.adyen.service.exception.ApiException;
import com.google.gson.Gson;

@Service
@Transactional
public class PayoutService {

    private final Logger log = LoggerFactory.getLogger(PayoutService.class);

    @Resource
    private Account adyenAccountService;

    @Resource
    private Fund adyenFundService;

    @Resource
    private AdyenPayoutErrorRepository adyenPayoutErrorRepository;

    @Resource
    private MiraklVoucherEntryRepository miraklVoucherEntryRepository;

    @Value("${payoutService.subscriptionTransferCode}")
    private String subscriptionTransferCode;

    @Value("${payoutService.liableAccountCode}")
    private String liableAccountCode;

    protected final static Gson GSON = new Gson();


    public void parseMiraklCsv(String csvData) throws IOException {
        Iterable<CSVRecord> records = CSVParser.parse(csvData, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'));
        for (CSVRecord record : records) {
            MiraklVoucherEntry miraklVoucherEntry = new MiraklVoucherEntry();

            miraklVoucherEntry.setShopId(record.get("shop-id"));
            miraklVoucherEntry.setTransferAmount(record.get("transfer-amount"));
            miraklVoucherEntry.setCurrencyIsoCode(record.get("currency-iso-code"));
            miraklVoucherEntry.setIban(record.get("payment-info-ibantype-iban"));
            miraklVoucherEntry.setInvoiceNumber(record.get("invoice-number"));
            miraklVoucherEntry.setShopName(record.get("shop-name"));
            miraklVoucherEntry.setSubscriptionAmount(record.get("subscription-amount"));

            miraklVoucherEntryRepository.save(miraklVoucherEntry);
        }

        miraklVoucherEntryRepository.flush();
    }

    @Async
    public synchronized void processMiraklVoucherEntries() {
        List<MiraklVoucherEntry> miraklVoucherEntries = miraklVoucherEntryRepository.findAll();
        for (MiraklVoucherEntry miraklVoucherEntry : miraklVoucherEntries) {
            processMiraklVoucherEntry(miraklVoucherEntry);
            miraklVoucherEntryRepository.delete(miraklVoucherEntry);
            miraklVoucherEntryRepository.flush();
        }
    }

    public void processMiraklVoucherEntry(MiraklVoucherEntry miraklVoucherEntry) {
        String accountHolderCode = miraklVoucherEntry.getShopId();

        PayoutAccountHolderRequest payoutAccountHolderRequest = null;
        PayoutAccountHolderResponse payoutAccountHolderResponse = null;
        TransferFundsRequest transferFundsRequest = null;

        try {
            //Call Adyen to retrieve the accountCode from the accountHolderCode
            GetAccountHolderResponse accountHolderResponse = getAccountHolderResponse(accountHolderCode);

            payoutAccountHolderRequest = createPayoutAccountHolderRequest(accountHolderResponse, miraklVoucherEntry);

            if (miraklVoucherEntry.hasSubscription()) {
                transferFundsRequest = createTransferFundsSubscription(accountHolderResponse, miraklVoucherEntry);
                TransferFundsResponse transferFundsResponse = adyenFundService.transferFunds(transferFundsRequest);
                log.info("Subscription submitted for accountHolder: [{}] + Response: [{}]", accountHolderCode, transferFundsResponse);
                transferFundsRequest = null;
            }
            payoutAccountHolderResponse = adyenFundService.payoutAccountHolder(payoutAccountHolderRequest);
            log.info("Payout submitted for accountHolder: [{}] + Psp ref: [{}]", accountHolderCode, payoutAccountHolderResponse.getPspReference());
        } catch (ApiException e) {
            log.error("MarketPay Api Exception: {}, {}. For the Shop: {}", e.getError(), e, accountHolderCode);
            storeAdyenPayoutError(payoutAccountHolderRequest, payoutAccountHolderResponse, transferFundsRequest);
        } catch (Exception e) {
            log.error("Exception: {}, {}. For the Shop: {}", e.getMessage(), e, accountHolderCode);
            storeAdyenPayoutError(payoutAccountHolderRequest, payoutAccountHolderResponse, transferFundsRequest);
        }

    }

    /**
     * Store Payout request into database so we can do retries
     */
    protected void storeAdyenPayoutError(PayoutAccountHolderRequest payoutAccountHolderRequest, PayoutAccountHolderResponse payoutAccountHolderResponse, TransferFundsRequest transferFundsRequest) {
        if (payoutAccountHolderRequest != null) {
            String rawRequest = GSON.toJson(payoutAccountHolderRequest);
            AdyenPayoutError adyenPayoutError = new AdyenPayoutError();

            adyenPayoutError.setAccountHolderCode(payoutAccountHolderRequest.getAccountHolderCode());
            adyenPayoutError.setRawRequest(rawRequest);


            if (payoutAccountHolderResponse != null) {
                String rawResponse = GSON.toJson(payoutAccountHolderResponse);
                adyenPayoutError.setRawResponse(rawResponse);
            }

            if (transferFundsRequest != null) {
                String subscriptionRawRequest = GSON.toJson(transferFundsRequest);
                adyenPayoutError.setRawSubscriptionRequest(subscriptionRawRequest);
            }


            adyenPayoutError.setProcessing(false);
            adyenPayoutError.setRetry(0);
            adyenPayoutErrorRepository.save(adyenPayoutError);
        }
    }

    protected PayoutAccountHolderRequest createPayoutAccountHolderRequest(GetAccountHolderResponse accountHolderResponse, MiraklVoucherEntry miraklVoucherEntry) throws Exception {

        //Retrieve the bankAccountUUID from Adyen matching to the iban provided from Mirakl
        String bankAccountUUID = getBankAccountUUID(accountHolderResponse, miraklVoucherEntry.getIban());
        PayoutAccountHolderRequest payoutAccountHolderRequest = new PayoutAccountHolderRequest();
        payoutAccountHolderRequest.setAccountCode(getAccountCode(accountHolderResponse));
        payoutAccountHolderRequest.setBankAccountUUID(bankAccountUUID);
        payoutAccountHolderRequest.setAccountHolderCode(accountHolderResponse.getAccountHolderCode());
        // make sure that you start with the invoiceNumber because long shopper statements could be stripped off
        String description = "Invoice number: "
            + miraklVoucherEntry.getInvoiceNumber()
            + ", Payout shop "
            + miraklVoucherEntry.getShopName()
            + " ("
            + accountHolderResponse.getAccountHolderCode()
            + ")";
        payoutAccountHolderRequest.setDescription(description);
        payoutAccountHolderRequest.setMerchantReference(miraklVoucherEntry.getInvoiceNumber());
        Amount adyenAmount = Util.createAmount(miraklVoucherEntry.getTransferAmount(), miraklVoucherEntry.getCurrencyIsoCode());
        payoutAccountHolderRequest.setAmount(adyenAmount);

        return payoutAccountHolderRequest;
    }

    protected GetAccountHolderResponse getAccountHolderResponse(String accountHolderCode) throws Exception {
        GetAccountHolderRequest getAccountHolderRequest = new GetAccountHolderRequest();
        getAccountHolderRequest.setAccountHolderCode(accountHolderCode);
        return adyenAccountService.getAccountHolder(getAccountHolderRequest);
    }

    private String getAccountCode(GetAccountHolderResponse accountHolderResponse) {
        return accountHolderResponse.getAccounts().get(0).getAccountCode();
    }

    protected String getBankAccountUUID(GetAccountHolderResponse accountHolderResponse, String iban) {
        //Iban Check
        List<BankAccountDetail> bankAccountDetailList = accountHolderResponse.getAccountHolderDetails().getBankAccountDetails();
        if (! bankAccountDetailList.isEmpty()) {
            for (BankAccountDetail bankAccountDetail : bankAccountDetailList) {
                if (bankAccountDetail.getIban().equals(iban)) {
                    return bankAccountDetail.getBankAccountUUID();
                }
            }
        }
        throw new IllegalStateException("No matching Iban between Mirakl and Adyen platforms.");
    }

    protected TransferFundsRequest createTransferFundsSubscription(GetAccountHolderResponse accountHolderResponse, MiraklVoucherEntry miraklVoucherEntry) throws Exception {

        TransferFundsRequest transferFundsRequest = new TransferFundsRequest();
        Amount adyenAmount = Util.createAmount(miraklVoucherEntry.getSubscriptionAmount(), miraklVoucherEntry.getCurrencyIsoCode());

        transferFundsRequest.setAmount(adyenAmount);

        transferFundsRequest.setSourceAccountCode(getAccountCode(accountHolderResponse));
        transferFundsRequest.setDestinationAccountCode(liableAccountCode);

        transferFundsRequest.setTransferCode(subscriptionTransferCode);
        return transferFundsRequest;
    }


}
