/*
 *                       ######
 *                       ######
 * ############    ####( ######  #####. ######  ############   ############
 * #############  #####( ######  #####. ######  #############  #############
 *        ######  #####( ######  #####. ######  #####  ######  #####  ######
 * ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 * ###### ######  #####( ######  #####. ######  #####          #####  ######
 * #############  #############  #############  #############  #####  ######
 *  ############   ############  #############   ############  #####  ######
 *                                      ######
 *                               #############
 *                               ############
 *
 * Adyen Mirakl Connector
 *
 * Copyright (c) 2018 Adyen B.V.
 * This file is open source and available under the MIT license.
 * See the LICENSE file for more info.
 *
 */

package com.adyen.mirakl.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import com.adyen.mirakl.MiraklShopFactory;
import com.adyen.mirakl.startup.MiraklStartupValidator;
import com.adyen.model.Address;
import com.adyen.model.Amount;
import com.adyen.model.Name;
import com.adyen.model.marketpay.AccountHolderDetails;
import com.adyen.model.marketpay.BankAccountDetail;
import com.adyen.model.marketpay.BusinessDetails;
import com.adyen.model.marketpay.CreateAccountHolderRequest;
import com.adyen.model.marketpay.CreateAccountHolderResponse;
import com.adyen.model.marketpay.DeleteBankAccountRequest;
import com.adyen.model.marketpay.DeleteBankAccountResponse;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.IndividualDetails;
import com.adyen.model.marketpay.ShareholderContact;
import com.adyen.model.marketpay.UpdateAccountHolderRequest;
import com.adyen.model.marketpay.UpdateAccountHolderResponse;
import com.adyen.model.marketpay.notification.CompensateNegativeBalanceNotificationRecord;
import com.adyen.service.Account;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mirakl.client.mmp.domain.accounting.document.MiraklAccountingDocumentType;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.common.currency.MiraklIsoCurrencyCode;
import com.mirakl.client.mmp.domain.invoice.MiraklInvoice;
import com.mirakl.client.mmp.domain.shop.MiraklContactInformation;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.operator.domain.invoice.MiraklCreatedManualAccountingDocumentReturn;
import com.mirakl.client.mmp.operator.domain.invoice.MiraklCreatedManualAccountingDocuments;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;
import static com.adyen.mirakl.MiraklShopFactory.UBO_FIELDS;
import static com.adyen.mirakl.MiraklShopFactory.UBO_FIELDS_ENUMS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ShopServiceTest {

    @InjectMocks
    private ShopService shopService;

    @Mock
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClientMock;
    @Mock
    private Account adyenAccountServiceMock;
    @Mock
    private GetAccountHolderResponse getAccountHolderResponseMock;
    @Mock
    private DeltaService deltaService;
    @Mock
    private Date dateMock;
    @Mock
    private CreateAccountHolderResponse createAccountHolderResponseMock;
    @Mock
    private UpdateAccountHolderResponse updateAccountHolderResponseMock;
    @Mock
    private ShareholderMappingService shareholderMappingService;
    @Mock
    private UboService uboServiceMock;
    @Mock
    private ShareholderContact shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4;
    @Mock
    private DocService docServiceMock;

    @Captor
    private ArgumentCaptor<CreateAccountHolderRequest> createAccountHolderRequestCaptor;
    @Captor
    private ArgumentCaptor<UpdateAccountHolderRequest> updateAccountHolderRequestCaptor;
    @Captor
    private ArgumentCaptor<DeleteBankAccountRequest> deleteBankAccountRequestCaptor;
    @Captor
    private ArgumentCaptor<MiraklGetShopsRequest> miraklGetShopsRequestCaptor;

    private MiraklShop shop;

    @Before
    public void setup() throws Exception {
        shopService.setHouseNumberPatterns(ImmutableMap.of("NL", Pattern.compile("\\s([a-zA-Z]*\\d+[a-zA-Z]*)$")));

        shop = new MiraklShop();
        when(adyenAccountServiceMock.deleteBankAccount(deleteBankAccountRequestCaptor.capture())).thenReturn(new DeleteBankAccountResponse());
    }

    @Test
    public void testDeleteBankAccountRequest() throws Exception {
        shop = new MiraklShop();
        GetAccountHolderResponse getAccountHolderResponse = new GetAccountHolderResponse();
        AccountHolderDetails accountHolderDetails = new AccountHolderDetails();
        List<BankAccountDetail> bankAccountDetails = new ArrayList<>();
        BankAccountDetail bankAccountDetail1 = new BankAccountDetail();
        bankAccountDetail1.setBankAccountUUID("0000-1111-2222");
        bankAccountDetails.add(bankAccountDetail1);
        BankAccountDetail bankAccountDetail2 = new BankAccountDetail();
        bankAccountDetail2.setBankAccountUUID("1111-2222-3333");
        bankAccountDetails.add(bankAccountDetail2);
        accountHolderDetails.setBankAccountDetails(bankAccountDetails);
        getAccountHolderResponse.setAccountHolderDetails(accountHolderDetails);

        boolean result = shopService.cleanUpBankAccounts(getAccountHolderResponse, shop);
        assertTrue(result);

        DeleteBankAccountRequest request = deleteBankAccountRequestCaptor.getValue();
        assertEquals("0000-1111-2222", request.getBankAccountUUIDs().get(0));
        assertEquals("1111-2222-3333", request.getBankAccountUUIDs().get(1));
    }

    @Test
    public void testRetrieveUpdatedShopsZeroShops() throws Exception {
        MiraklShops miraklShops = new MiraklShops();
        List<MiraklShop> shops = new ArrayList<>();
        miraklShops.setShops(shops);
        miraklShops.setTotalCount(0L);

        when(miraklMarketplacePlatformOperatorApiClientMock.getShops(any())).thenReturn(miraklShops);

        shopService.processUpdatedShops();
        verify(adyenAccountServiceMock, never()).createAccountHolder(any());
    }

    @Test
    public void testRetrieveUpdatedShopsCreate() throws Exception {
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(MiraklStartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE));
        additionalField.setValue(MiraklStartupValidator.AdyenLegalEntityType.INDIVIDUAL.toString());
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalFieldDob = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalFieldDob.setCode("adyen-individual-dob");
        additionalFieldDob.setValue("1989-03-15T23:00:00Z");
        setup(ImmutableList.of(additionalField, additionalFieldDob));
        when(adyenAccountServiceMock.createAccountHolder(createAccountHolderRequestCaptor.capture())).thenReturn(createAccountHolderResponseMock);
        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn("");

        shopService.processUpdatedShops();
        CreateAccountHolderRequest request = createAccountHolderRequestCaptor.getValue();

        verify(adyenAccountServiceMock).createAccountHolder(request);

        assertEquals("id", request.getAccountHolderCode());
        assertEquals(CreateAccountHolderRequest.LegalEntityEnum.INDIVIDUAL, request.getLegalEntity());
        assertNotNull(request.getAccountHolderDetails().getIndividualDetails());
        IndividualDetails individualDetails = request.getAccountHolderDetails().getIndividualDetails();
        assertEquals("firstName", individualDetails.getName().getFirstName());
        assertEquals("lastName", individualDetails.getName().getLastName());
        assertEquals(Name.GenderEnum.FEMALE, individualDetails.getName().getGender());

        final Address address = request.getAccountHolderDetails().getAddress();
        Assertions.assertThat(address.getHouseNumberOrName()).isEqualTo("610b");
        Assertions.assertThat(address.getPostalCode()).isEqualTo("zipCode");
        Assertions.assertThat(address.getStreet()).isEqualTo("Kosterpark");
        Assertions.assertThat(address.getCountry()).isEqualTo("NL");
        Assertions.assertThat(address.getCity()).isEqualTo("city");

        final List<BankAccountDetail> bankAccountDetails = request.getAccountHolderDetails().getBankAccountDetails();
        Assertions.assertThat(bankAccountDetails.size()).isEqualTo(1);
        final BankAccountDetail bankDetails = bankAccountDetails.iterator().next();
        Assertions.assertThat(bankDetails.getOwnerPostalCode()).isEqualTo("zipCode");
        Assertions.assertThat(bankDetails.getOwnerCity()).isEqualTo("city");
        Assertions.assertThat(bankDetails.getOwnerName()).isEqualTo("owner");
        Assertions.assertThat(bankDetails.getBankBicSwift()).isEqualTo("BIC");
        Assertions.assertThat(bankDetails.getCountryCode()).isEqualTo("GB");
        Assertions.assertThat(bankDetails.getOwnerHouseNumberOrName()).isEqualTo("610b");
        Assertions.assertThat(bankDetails.getIban()).isEqualTo("GB00IBAN");
        Assertions.assertThat(bankDetails.getCurrencyCode()).isEqualTo("EUR");
        Assertions.assertThat(bankDetails.getBankCity()).isEqualTo("bankCity");
        Assertions.assertThat(individualDetails.getPersonalData().getDateOfBirth()).isEqualTo("1989-03-15T23:00:00Z");
    }

    @Test
    public void testRetrieveUpdatedShopsUpdate() throws Exception {
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(MiraklStartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE));
        additionalField.setValue(MiraklStartupValidator.AdyenLegalEntityType.BUSINESS.toString());

        List<MiraklAdditionalFieldValue> uboFields = MiraklShopFactory.createMiraklAdditionalUboField("1", UBO_FIELDS, UBO_FIELDS_ENUMS);
        final ImmutableList<MiraklAdditionalFieldValue> additionalFields = new ImmutableList.Builder<MiraklAdditionalFieldValue>().add(additionalField).addAll(uboFields).build();

        setup(additionalFields);
        when(adyenAccountServiceMock.updateAccountHolder(updateAccountHolderRequestCaptor.capture())).thenReturn(updateAccountHolderResponseMock);
        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn("alreadyExisting");
        when(uboServiceMock.extractUbos(any(), any())).thenReturn(ImmutableList.of(shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4));

        shopService.processUpdatedShops();

        UpdateAccountHolderRequest request = updateAccountHolderRequestCaptor.getValue();
        verify(adyenAccountServiceMock).updateAccountHolder(request);
        verify(shareholderMappingService).updateShareholderMapping(updateAccountHolderResponseMock, shop);
        verify(docServiceMock).retryDocumentsForShop("id");
        assertEquals("id", request.getAccountHolderCode());
        final List<ShareholderContact> shareholders = request.getAccountHolderDetails().getBusinessDetails().getShareholders();
        Assertions.assertThat(shareholders).containsExactlyInAnyOrder(shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4);
    }

    @Test
    public void testRetrieveUpdatedShopsPagination() throws Exception {
        //Response contains one shop and total_count = 2
        MiraklShops miraklShops = new MiraklShops();
        List<MiraklShop> shops = new ArrayList<>();
        miraklShops.setShops(shops);
        shops.add(new MiraklShop());
        miraklShops.setTotalCount(2L);

        when(deltaService.getShopDelta()).thenReturn(dateMock);
        when(miraklMarketplacePlatformOperatorApiClientMock.getShops(miraklGetShopsRequestCaptor.capture())).thenReturn(miraklShops);

        List<MiraklShop> updatedShops = shopService.getUpdatedShops();

        verify(deltaService, times(2)).getShopDelta();

        assertEquals(2, updatedShops.size());

        List<MiraklGetShopsRequest> miraklGetShopsRequests = miraklGetShopsRequestCaptor.getAllValues();
        assertEquals(2, miraklGetShopsRequests.size());

        assertEquals(0L, miraklGetShopsRequests.get(0).getOffset());
        assertEquals(dateMock, miraklGetShopsRequests.get(0).getUpdatedSince());
        assertEquals(true, miraklGetShopsRequests.get(0).isPaginate());

        assertEquals(1L, miraklGetShopsRequests.get(1).getOffset());
        assertEquals(dateMock, miraklGetShopsRequests.get(1).getUpdatedSince());
        assertEquals(true, miraklGetShopsRequests.get(1).isPaginate());
    }

    @Test
    public void testUpdateAccountHolderRequest() {
        shop.setId("id");
        shop.setCurrencyIsoCode(MiraklIsoCurrencyCode.EUR);

        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(MiraklStartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE));
        additionalField.setValue(MiraklStartupValidator.AdyenLegalEntityType.BUSINESS.toString());

        List<MiraklAdditionalFieldValue> additionalFields = MiraklShopFactory.createMiraklAdditionalUboField("1", UBO_FIELDS, UBO_FIELDS_ENUMS);
        additionalFields.add(additionalField);
        shop.setAdditionalFieldValues(additionalFields);

        MiraklContactInformation miraklContactInformation = createMiraklContactInformation();
        shop.setContactInformation(miraklContactInformation);

        MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = createMiraklIbanBankAccountInformation();
        shop.setPaymentInformation(miraklIbanBankAccountInformation);

        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn(null);
        when(uboServiceMock.extractUbos(any(), any())).thenReturn(ImmutableList.of(shareHolderMock1));

        // Update with no IBAN yet
        UpdateAccountHolderRequest request = shopService.updateAccountHolderRequestFromShop(shop, getAccountHolderResponseMock);

        assertEquals("id", request.getAccountHolderCode());
        assertEquals("GB", request.getAccountHolderDetails().getBankAccountDetails().get(0).getCountryCode());
        assertEquals("owner", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerName());
        assertEquals("GB00IBAN", request.getAccountHolderDetails().getBankAccountDetails().get(0).getIban());
        assertEquals("BIC", request.getAccountHolderDetails().getBankAccountDetails().get(0).getBankBicSwift());
        assertEquals("1111AA", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerPostalCode());
        assertEquals("Amsterdam", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerCity());
        assertEquals("BIC", request.getAccountHolderDetails().getBankAccountDetails().get(0).getBankBicSwift());
        assertEquals("610b", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerHouseNumberOrName());

        // Update with the same BankAccountDetails
        GetAccountHolderResponse getAccountHolderResponse = createGetAccountHolderResponse();
        getAccountHolderResponse.getAccountHolderDetails().setBankAccountDetails(request.getAccountHolderDetails().getBankAccountDetails());

        UpdateAccountHolderRequest requestWithoutIbanChange = shopService.updateAccountHolderRequestFromShop(shop, getAccountHolderResponse);
        Assertions.assertThat(requestWithoutIbanChange.getAccountHolderDetails().getBankAccountDetails()).isEmpty();

        // Update with a different IBAN
        getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().get(0).setIban("GBDIFFERENTIBAN");

        UpdateAccountHolderRequest requestWithIbanChange = shopService.updateAccountHolderRequestFromShop(shop, getAccountHolderResponse);
        assertEquals(1, requestWithIbanChange.getAccountHolderDetails().getBankAccountDetails().size());
        assertEquals("GB00IBAN", requestWithIbanChange.getAccountHolderDetails().getBankAccountDetails().get(0).getIban());
    }

    @Test
    public void testCompensateNegativeBalance() throws Exception {
        Amount amount = new Amount();
        String currency = "EUR";
        amount.setCurrency(currency);
        amount.setValue(new Long("-100"));
        CompensateNegativeBalanceNotificationRecord compensateNegativeBalanceNotificationRecord = new CompensateNegativeBalanceNotificationRecord();
        compensateNegativeBalanceNotificationRecord.setAccountCode("134846738");
        compensateNegativeBalanceNotificationRecord.setAmount(amount);
        compensateNegativeBalanceNotificationRecord.setTransferDate(new Date());

        MiraklCreatedManualAccountingDocuments miraklCreatedManualAccountingDocumentsMock = createManualCreditDocument(amount);
        GetAccountHolderResponse getAccountHolderResponse = new GetAccountHolderResponse();
        getAccountHolderResponse.setAccountHolderCode("123321");
        when(adyenAccountServiceMock.getAccountHolder(any())).thenReturn(getAccountHolderResponse);
        when(miraklMarketplacePlatformOperatorApiClientMock.createManualAccountingDocument(any())).thenReturn(miraklCreatedManualAccountingDocumentsMock);

        MiraklCreatedManualAccountingDocuments miraklCreatedManualAccountingDocuments = shopService.processCompensateNegativeBalance(compensateNegativeBalanceNotificationRecord, "123456789");

        assertEquals(MiraklAccountingDocumentType.MANUAL_CREDIT, miraklCreatedManualAccountingDocuments.getManualAccountingDocumentReturns().get(0).getManualAccountingDocument().getType());
        assertEquals(currency, miraklCreatedManualAccountingDocuments.getManualAccountingDocumentReturns().get(0).getManualAccountingDocument().getCurrencyIsoCode().toString());
        assertNull(miraklCreatedManualAccountingDocuments.getManualAccountingDocumentReturns().get(0).getManualAccountingDocumentError());
    }

    private MiraklCreatedManualAccountingDocuments createManualCreditDocument(Amount amount){
        MiraklCreatedManualAccountingDocuments miraklCreatedManualAccountingDocuments = new MiraklCreatedManualAccountingDocuments();
        MiraklCreatedManualAccountingDocumentReturn miraklCreatedManualAccountingDocumentReturn = new MiraklCreatedManualAccountingDocumentReturn();
        MiraklInvoice miraklCreditInvoice = new MiraklInvoice();
        miraklCreditInvoice.setCurrencyIsoCode(MiraklIsoCurrencyCode.valueOf(amount.getCurrency()));
        miraklCreditInvoice.setType(MiraklAccountingDocumentType.MANUAL_CREDIT);
        miraklCreditInvoice.setTechnicalId("technicalid");
        miraklCreatedManualAccountingDocumentReturn.setManualAccountingDocument(miraklCreditInvoice);
        List<MiraklCreatedManualAccountingDocumentReturn> miraklCreatedManualAccountingDocumentReturnList = new ArrayList<>();
        miraklCreatedManualAccountingDocumentReturnList.add(miraklCreatedManualAccountingDocumentReturn);
        miraklCreatedManualAccountingDocuments.setManualAccountingDocumentReturns(miraklCreatedManualAccountingDocumentReturnList);
        return miraklCreatedManualAccountingDocuments;
    }

    private GetAccountHolderResponse createGetAccountHolderResponse() {
        GetAccountHolderResponse getAccountHolderResponse = new GetAccountHolderResponse();
        AccountHolderDetails accountHolderDetails = new AccountHolderDetails();
        List<BankAccountDetail> bankAccountDetails = new ArrayList<BankAccountDetail>();
        BankAccountDetail bankAccountDetail1 = new BankAccountDetail();
        bankAccountDetail1.setBankAccountUUID("0000-1111-2222");
        bankAccountDetail1.setIban("GB00IBAN");
        bankAccountDetails.add(bankAccountDetail1);
        accountHolderDetails.setBankAccountDetails(bankAccountDetails);
        getAccountHolderResponse.setAccountHolderDetails(accountHolderDetails);
        return getAccountHolderResponse;
    }


    @Test
    public void shouldCreateBusinessAccount() throws Exception {
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(MiraklStartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE));
        additionalField.setValue(MiraklStartupValidator.AdyenLegalEntityType.BUSINESS.toString());

        setup(ImmutableList.of(additionalField));

        when(adyenAccountServiceMock.createAccountHolder(createAccountHolderRequestCaptor.capture())).thenReturn(createAccountHolderResponseMock);
        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn("");
        when(uboServiceMock.extractUbos(any(), any())).thenReturn(ImmutableList.of(shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4));

        shopService.processUpdatedShops();

        verify(deltaService).updateShopDelta(any(ZonedDateTime.class));
        verify(shareholderMappingService).updateShareholderMapping(createAccountHolderResponseMock, shop);

        List<ShareholderContact> shareHolders = createAccountHolderRequestCaptor.getAllValues()
                                                                                .stream()
                                                                                .map(CreateAccountHolderRequest::getAccountHolderDetails)
                                                                                .map(AccountHolderDetails::getBusinessDetails)
                                                                                .map(BusinessDetails::getShareholders)
                                                                                .flatMap(Collection::stream)
                                                                                .collect(Collectors.toList());

        Assertions.assertThat(shareHolders).containsExactlyInAnyOrder(shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4);

    }

    @Test
    public void missingUbos() throws Exception {
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(MiraklStartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE));
        additionalField.setValue(MiraklStartupValidator.AdyenLegalEntityType.BUSINESS.toString());

        setup(ImmutableList.of(additionalField));

        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn("");
        when(uboServiceMock.extractUbos(any(), any())).thenReturn(null);

        shopService.processUpdatedShops();

        verify(deltaService).updateShopDelta(any(ZonedDateTime.class));
        verify(adyenAccountServiceMock, never()).createAccountHolder(any());
    }

    private MiraklIbanBankAccountInformation createMiraklIbanBankAccountInformation() {
        MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = new MiraklIbanBankAccountInformation();
        miraklIbanBankAccountInformation.setIban("GB00IBAN");
        miraklIbanBankAccountInformation.setBic("BIC");
        miraklIbanBankAccountInformation.setOwner("owner");
        miraklIbanBankAccountInformation.setBankCity("bankCity");
        return miraklIbanBankAccountInformation;
    }

    private MiraklContactInformation createMiraklContactInformation() {
        MiraklContactInformation miraklContactInformation = new MiraklContactInformation();
        miraklContactInformation.setEmail("email");
        miraklContactInformation.setFirstname("firstName");
        miraklContactInformation.setLastname("lastName");
        miraklContactInformation.setStreet1("Kosterpark 610b");
        miraklContactInformation.setZipCode("1111AA");
        miraklContactInformation.setCity("Amsterdam");
        miraklContactInformation.setCountry("NLD");
        miraklContactInformation.setCivility("Mrs");
        return miraklContactInformation;
    }

    private void setup(List<MiraklAdditionalFieldValue> additionalFields) throws Exception {
        MiraklShops miraklShops = new MiraklShops();
        List<MiraklShop> shops = new ArrayList<>();
        miraklShops.setShops(shops);
        miraklShops.setTotalCount(1L);

        shops.add(shop);

        MiraklContactInformation contactInformation = new MiraklContactInformation();
        contactInformation.setEmail("email");
        contactInformation.setFirstname("firstName");
        contactInformation.setLastname("lastName");
        contactInformation.setCountry("NLD");
        contactInformation.setCivility("Mrs");
        contactInformation.setCity("city");
        contactInformation.setStreet1("Kosterpark 610b");
        contactInformation.setZipCode("zipCode");
        contactInformation.setState("state");


        shop.setContactInformation(contactInformation);

        shop.setAdditionalFieldValues(additionalFields);
        shop.setId("id");
        shop.setCurrencyIsoCode(MiraklIsoCurrencyCode.EUR);

        MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = createMiraklIbanBankAccountInformation();
        shop.setPaymentInformation(miraklIbanBankAccountInformation);

        when(miraklMarketplacePlatformOperatorApiClientMock.getShops(any())).thenReturn(miraklShops);
        when(adyenAccountServiceMock.getAccountHolder(any())).thenReturn(getAccountHolderResponseMock);
    }


}

