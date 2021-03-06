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
import com.adyen.mirakl.config.ApplicationProperties;
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
import com.mirakl.client.mmp.domain.shop.bank.MiraklAbaBankAccountInformation;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import com.mirakl.client.mmp.domain.shop.bank.MiraklUkBankAccountInformation;
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
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ShopServiceTest {

    @InjectMocks
    private ShopService shopService;

    @Mock
    private ApplicationProperties applicationProperties;
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
    private ShareholderContact shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4, shareHolderMockUS, shareHolderMockUK;
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
    private MiraklShop miraklShopUS;
    private MiraklShop miraklShopUK;

    private static final int GENERIC_SHOP_INDEX = 0;
    private static final int US_SHOP_INDEX = 1;
    private static final int UK_SHOP_INDEX = 2;

    @Before
    public void setup() throws Exception {
        shopService.setHouseNumberPatterns(ImmutableMap.of("NL", Pattern.compile("\\s([a-zA-Z]*\\d+[a-zA-Z]*)$")));

        shop = new MiraklShop();
        miraklShopUS = new MiraklShop();
        miraklShopUK = new MiraklShop();

        when(adyenAccountServiceMock.deleteBankAccount(deleteBankAccountRequestCaptor.capture())).thenReturn(new DeleteBankAccountResponse());
        when(applicationProperties.getDefaultProcessingTier()).thenReturn(null);
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
        setup(ImmutableList.of(additionalField));
        when(adyenAccountServiceMock.createAccountHolder(createAccountHolderRequestCaptor.capture())).thenReturn(createAccountHolderResponseMock);
        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn("");

        shopService.processUpdatedShops();
        List<CreateAccountHolderRequest> requests = createAccountHolderRequestCaptor.getAllValues();

        assertNotNull(requests);
        assertEquals(3, requests.size());
        CreateAccountHolderRequest genericRequest = requests.get(GENERIC_SHOP_INDEX);
        CreateAccountHolderRequest USRequest = requests.get(US_SHOP_INDEX);
        CreateAccountHolderRequest UKRequest = requests.get(UK_SHOP_INDEX);

        verify(adyenAccountServiceMock).createAccountHolder(genericRequest);
        assertEquals("id", genericRequest.getAccountHolderCode());
        assertEquals(CreateAccountHolderRequest.LegalEntityEnum.INDIVIDUAL, genericRequest.getLegalEntity());
        assertNotNull(genericRequest.getAccountHolderDetails().getIndividualDetails());
        IndividualDetails individualDetails = genericRequest.getAccountHolderDetails().getIndividualDetails();

        assertEquals("firstName", individualDetails.getName().getFirstName());
        assertEquals("lastName", individualDetails.getName().getLastName());
        assertEquals(Name.GenderEnum.FEMALE, individualDetails.getName().getGender());

        final Address address = genericRequest.getAccountHolderDetails().getAddress();
        Assertions.assertThat(address.getHouseNumberOrName()).isEqualTo("610b");
        Assertions.assertThat(address.getPostalCode()).isEqualTo("zipCode");
        Assertions.assertThat(address.getStreet()).isEqualTo("Kosterpark");
        Assertions.assertThat(address.getCountry()).isEqualTo("NL");
        Assertions.assertThat(address.getCity()).isEqualTo("city");

        final List<BankAccountDetail> bankAccountDetails = genericRequest.getAccountHolderDetails().getBankAccountDetails();
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

        verify(adyenAccountServiceMock).createAccountHolder(USRequest);
        verify(adyenAccountServiceMock).createAccountHolder(UKRequest);

        Assertions.assertThat(USRequest.getAccountHolderDetails().getBankAccountDetails().get(0).getAccountNumber()).isEqualTo("1234567890");
        Assertions.assertThat(UKRequest.getAccountHolderDetails().getBankAccountDetails().get(0).getAccountNumber()).isEqualTo("75849855");
        // we should only populate the processing tier if explicitly configured
        Assertions.assertThat(genericRequest.getProcessingTier()).isNull();
        Assertions.assertThat(USRequest.getProcessingTier()).isNull();
        Assertions.assertThat(UKRequest.getProcessingTier()).isNull();
    }

    @Test
    public void testRetrieveUpdatedShopsCreateWithDefaultTier() throws Exception {
        when(applicationProperties.getDefaultProcessingTier()).thenReturn(3);

        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(MiraklStartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE));
        additionalField.setValue(MiraklStartupValidator.AdyenLegalEntityType.INDIVIDUAL.toString());
        setup(ImmutableList.of(additionalField));
        when(adyenAccountServiceMock.createAccountHolder(createAccountHolderRequestCaptor.capture())).thenReturn(createAccountHolderResponseMock);
        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn("");

        shopService.processUpdatedShops();
        List<CreateAccountHolderRequest> requests = createAccountHolderRequestCaptor.getAllValues();

        assertNotNull(requests);
        assertEquals(3, requests.size());
        CreateAccountHolderRequest genericRequest = requests.get(GENERIC_SHOP_INDEX);
        CreateAccountHolderRequest USRequest = requests.get(US_SHOP_INDEX);
        CreateAccountHolderRequest UKRequest = requests.get(UK_SHOP_INDEX);

        verify(adyenAccountServiceMock).createAccountHolder(genericRequest);
        verify(adyenAccountServiceMock).createAccountHolder(USRequest);
        verify(adyenAccountServiceMock).createAccountHolder(UKRequest);

        Assertions.assertThat(genericRequest.getProcessingTier()).isEqualTo(3);
        Assertions.assertThat(USRequest.getProcessingTier()).isEqualTo(3);
        Assertions.assertThat(UKRequest.getProcessingTier()).isEqualTo(3);
    }

    @Test
    public void testRetrieveUpdatedShopsUpdate() throws Exception {
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(MiraklStartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE));
        additionalField.setValue(MiraklStartupValidator.AdyenLegalEntityType.BUSINESS.toString());

        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalFieldDoingBusinessAs = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalFieldDoingBusinessAs.setCode(MiraklStartupValidator.CustomMiraklFields.ADYEN_BUSINESS_REGISTEREDNAME.toString());
        additionalFieldDoingBusinessAs.setValue("Different from legalBusinessName");

        List<MiraklAdditionalFieldValue> uboFields = MiraklShopFactory.createMiraklAdditionalUboField("1", UBO_FIELDS, UBO_FIELDS_ENUMS);
        final ImmutableList<MiraklAdditionalFieldValue> additionalFields = new ImmutableList.Builder<MiraklAdditionalFieldValue>().add(additionalField).add(additionalFieldDoingBusinessAs).addAll(uboFields).build();

        setup(additionalFields);
        when(adyenAccountServiceMock.updateAccountHolder(updateAccountHolderRequestCaptor.capture())).thenReturn(updateAccountHolderResponseMock);
        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn("alreadyExisting");
        when(uboServiceMock.extractUbos(eq(shop), any())).thenReturn(ImmutableList.of(shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4));
        when(uboServiceMock.extractUbos(eq(miraklShopUS), any())).thenReturn(ImmutableList.of(shareHolderMockUS));
        when(uboServiceMock.extractUbos(eq(miraklShopUK), any())).thenReturn(ImmutableList.of(shareHolderMockUK));

        shopService.processUpdatedShops();

        List<UpdateAccountHolderRequest> requests = updateAccountHolderRequestCaptor.getAllValues();
        assertEquals(3, requests.size());

        UpdateAccountHolderRequest genericRequest = requests.get(GENERIC_SHOP_INDEX);
        UpdateAccountHolderRequest USRequest = requests.get(US_SHOP_INDEX);
        UpdateAccountHolderRequest UKRequest = requests.get(UK_SHOP_INDEX);

        verify(adyenAccountServiceMock).updateAccountHolder(genericRequest);
        verify(shareholderMappingService).updateShareholderMapping(updateAccountHolderResponseMock, shop);
        verify(shareholderMappingService).updateShareholderMapping(updateAccountHolderResponseMock, miraklShopUS);
        verify(shareholderMappingService).updateShareholderMapping(updateAccountHolderResponseMock, miraklShopUK);

        verify(docServiceMock).retryDocumentsForShop("id");
        assertEquals("id", genericRequest.getAccountHolderCode());
        assertEquals("Different from legalBusinessName", genericRequest.getAccountHolderDetails().getBusinessDetails().getDoingBusinessAs());
        final List<ShareholderContact> shareholders = genericRequest.getAccountHolderDetails().getBusinessDetails().getShareholders();
        Assertions.assertThat(shareholders).containsExactlyInAnyOrder(shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4);

        verify(adyenAccountServiceMock).updateAccountHolder(USRequest);
        verify(docServiceMock).retryDocumentsForShop("1");
        assertEquals("1", USRequest.getAccountHolderCode());
        assertEquals("Different from legalBusinessName", USRequest.getAccountHolderDetails().getBusinessDetails().getDoingBusinessAs());
        final List<ShareholderContact> shareHoldersUS = USRequest.getAccountHolderDetails().getBusinessDetails().getShareholders();
        Assertions.assertThat(shareHoldersUS).containsExactlyInAnyOrder(shareHolderMockUS);

        verify(adyenAccountServiceMock).updateAccountHolder(UKRequest);
        verify(docServiceMock).retryDocumentsForShop("2");
        assertEquals("2", UKRequest.getAccountHolderCode());
        assertEquals("Different from legalBusinessName", UKRequest.getAccountHolderDetails().getBusinessDetails().getDoingBusinessAs());
        final List<ShareholderContact> shareHoldersUK = UKRequest.getAccountHolderDetails().getBusinessDetails().getShareholders();
        Assertions.assertThat(shareHoldersUK).containsExactlyInAnyOrder(shareHolderMockUK);
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
        assertEquals("1111AA", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerPostalCode());
        assertEquals("Amsterdam", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerCity());
        assertEquals("BIC", request.getAccountHolderDetails().getBankAccountDetails().get(0).getBankBicSwift());
        assertEquals("bankCity", request.getAccountHolderDetails().getBankAccountDetails().get(0).getBankCity());
        assertEquals("610b", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerHouseNumberOrName());
        assertEquals("state", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerState());
        assertEquals("NL", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerCountryCode());

        // Update with the same BankAccountDetails
        GetAccountHolderResponse getAccountHolderResponse = createGetAccountHolderResponse();
        getAccountHolderResponse.getAccountHolderDetails().setBankAccountDetails(request.getAccountHolderDetails().getBankAccountDetails());

        UpdateAccountHolderRequest requestWithoutIbanChange = shopService.updateAccountHolderRequestFromShop(shop, getAccountHolderResponse);
        Assertions.assertThat(requestWithoutIbanChange.getAccountHolderDetails().getBankAccountDetails()).isNullOrEmpty();

        // Update with a different IBAN
        getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().get(0).setIban("GBDIFFERENTIBAN");

        UpdateAccountHolderRequest requestWithIbanChange = shopService.updateAccountHolderRequestFromShop(shop, getAccountHolderResponse);
        assertEquals(1, requestWithIbanChange.getAccountHolderDetails().getBankAccountDetails().size());
        assertEquals("GB00IBAN", requestWithIbanChange.getAccountHolderDetails().getBankAccountDetails().get(0).getIban());
    }

    @Test
    public void testUpdateAccountHolderRequestUS() {
        miraklShopUS.setId("1");
        miraklShopUS.setCurrencyIsoCode(MiraklIsoCurrencyCode.USD);

        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(MiraklStartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE));
        additionalField.setValue(MiraklStartupValidator.AdyenLegalEntityType.BUSINESS.toString());

        List<MiraklAdditionalFieldValue> additionalFields = MiraklShopFactory.createMiraklAdditionalUboField("1", UBO_FIELDS, UBO_FIELDS_ENUMS);
        additionalFields.add(additionalField);
        miraklShopUS.setAdditionalFieldValues(additionalFields);

        MiraklContactInformation miraklContactInformation = createMiraklContactInformation();
        miraklShopUS.setContactInformation(miraklContactInformation);

        MiraklAbaBankAccountInformation miraklAbaBankAccountInformation = createMiraklAbaBankAccountInformation();
        miraklShopUS.setPaymentInformation(miraklAbaBankAccountInformation);

        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn(null);
        when(uboServiceMock.extractUbos(eq(miraklShopUS), any())).thenReturn(ImmutableList.of(shareHolderMockUS));

        UpdateAccountHolderRequest request = shopService.updateAccountHolderRequestFromShop(miraklShopUS, getAccountHolderResponseMock);
        BankAccountDetail bankAccountDetail = request.getAccountHolderDetails().getBankAccountDetails().get(0);

        assertEquals("1", request.getAccountHolderCode());
        assertEquals("US", bankAccountDetail.getCountryCode());
        assertEquals("owner", bankAccountDetail.getOwnerName());
        assertEquals("1234567890", bankAccountDetail.getAccountNumber());
        assertEquals("121000358", bankAccountDetail.getBranchCode());
        assertEquals("Amsterdam", bankAccountDetail.getBankCity());
        assertEquals("TestBank", bankAccountDetail.getBankName());
        assertEquals("610b", bankAccountDetail.getOwnerHouseNumberOrName());
        assertEquals("Kosterpark", bankAccountDetail.getOwnerStreet());
        assertEquals("state", bankAccountDetail.getOwnerState());
        assertEquals("NL", bankAccountDetail.getOwnerCountryCode());
        assertEquals("Amsterdam", bankAccountDetail.getOwnerCity());
        assertEquals("1111AA", bankAccountDetail.getOwnerPostalCode());
        assertEquals("USD", bankAccountDetail.getCurrencyCode());

        // Update with the same BankAccountDetails
        GetAccountHolderResponse getAccountHolderResponse = createGetAccountHolderResponse();
        getAccountHolderResponse.getAccountHolderDetails().setBankAccountDetails(request.getAccountHolderDetails().getBankAccountDetails());
        UpdateAccountHolderRequest requestWithoutAccountChange = shopService.updateAccountHolderRequestFromShop(miraklShopUS, getAccountHolderResponse);
        Assertions.assertThat(requestWithoutAccountChange.getAccountHolderDetails().getBankAccountDetails()).isNullOrEmpty();

        // Update with a different AccountNumber
        getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().get(0).setAccountNumber("1234567891");
        getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().get(0).setBranchCode("121000359");

        UpdateAccountHolderRequest requestWithAccountNumberChange = shopService.updateAccountHolderRequestFromShop(miraklShopUS, getAccountHolderResponse);
        assertEquals(1, requestWithAccountNumberChange.getAccountHolderDetails().getBankAccountDetails().size());
        assertEquals("1234567890", requestWithAccountNumberChange.getAccountHolderDetails().getBankAccountDetails().get(0).getAccountNumber());
        assertEquals("121000358", requestWithAccountNumberChange.getAccountHolderDetails().getBankAccountDetails().get(0).getBranchCode());
    }

    @Test
    public void testUpdateAccountHolderRequestUK() {
        miraklShopUK.setId("2");
        miraklShopUK.setCurrencyIsoCode(MiraklIsoCurrencyCode.GBP);

        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(MiraklStartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE));
        additionalField.setValue(MiraklStartupValidator.AdyenLegalEntityType.BUSINESS.toString());

        List<MiraklAdditionalFieldValue> additionalFields = MiraklShopFactory.createMiraklAdditionalUboField("1", UBO_FIELDS, UBO_FIELDS_ENUMS);
        additionalFields.add(additionalField);
        miraklShopUK.setAdditionalFieldValues(additionalFields);

        MiraklContactInformation miraklContactInformation = createMiraklContactInformation();
        miraklShopUK.setContactInformation(miraklContactInformation);

        MiraklUkBankAccountInformation miraklUkBankAccountInformation = createMiraklUkBankAccountInformation();
        miraklShopUK.setPaymentInformation(miraklUkBankAccountInformation);

        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn(null);
        when(uboServiceMock.extractUbos(eq(miraklShopUK), any())).thenReturn(ImmutableList.of(shareHolderMockUK));

        UpdateAccountHolderRequest request = shopService.updateAccountHolderRequestFromShop(miraklShopUK, getAccountHolderResponseMock);
        BankAccountDetail bankAccountDetail = request.getAccountHolderDetails().getBankAccountDetails().get(0);

        assertEquals("2", request.getAccountHolderCode());
        assertEquals("GB", bankAccountDetail.getCountryCode());
        assertEquals("TestData", bankAccountDetail.getOwnerName());
        assertEquals("75849855", bankAccountDetail.getAccountNumber());
        assertEquals("200052", bankAccountDetail.getBranchCode());
        assertEquals("PASSED", bankAccountDetail.getBankCity());
        assertEquals("PASSED", bankAccountDetail.getBankName());
        assertEquals("610b", bankAccountDetail.getOwnerHouseNumberOrName());
        assertEquals("Kosterpark", bankAccountDetail.getOwnerStreet());
        assertEquals("state", bankAccountDetail.getOwnerState());
        assertEquals("NL", bankAccountDetail.getOwnerCountryCode());
        assertEquals("Amsterdam", bankAccountDetail.getOwnerCity());
        assertEquals("1111AA", bankAccountDetail.getOwnerPostalCode());
        assertEquals("GBP", bankAccountDetail.getCurrencyCode());

        // Update with the same BankAccountDetails
        GetAccountHolderResponse getAccountHolderResponse = createGetAccountHolderResponse();
        getAccountHolderResponse.getAccountHolderDetails().setBankAccountDetails(request.getAccountHolderDetails().getBankAccountDetails());
        UpdateAccountHolderRequest requestWithoutAccountChange = shopService.updateAccountHolderRequestFromShop(miraklShopUK, getAccountHolderResponse);
        Assertions.assertThat(requestWithoutAccountChange.getAccountHolderDetails().getBankAccountDetails()).isNullOrEmpty();

        // Update with a different AccountNumber
        getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().get(0).setAccountNumber("75849856");
        getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().get(0).setBranchCode("200053");

        UpdateAccountHolderRequest requestWithAccountNumberChange = shopService.updateAccountHolderRequestFromShop(miraklShopUK, getAccountHolderResponse);
        assertEquals(1, requestWithAccountNumberChange.getAccountHolderDetails().getBankAccountDetails().size());
        assertEquals("75849855", requestWithAccountNumberChange.getAccountHolderDetails().getBankAccountDetails().get(0).getAccountNumber());
        assertEquals("200052", requestWithAccountNumberChange.getAccountHolderDetails().getBankAccountDetails().get(0).getBranchCode());
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

    private MiraklCreatedManualAccountingDocuments createManualCreditDocument(Amount amount) {
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
        when(uboServiceMock.extractUbos(eq(shop), any())).thenReturn(ImmutableList.of(shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4));
        when(uboServiceMock.extractUbos(eq(miraklShopUS), any())).thenReturn(ImmutableList.of(shareHolderMockUS));
        when(uboServiceMock.extractUbos(eq(miraklShopUK), any())).thenReturn(ImmutableList.of(shareHolderMockUK));

        shopService.processUpdatedShops();

        verify(deltaService).updateShopDelta(any(ZonedDateTime.class));
        verify(shareholderMappingService).updateShareholderMapping(createAccountHolderResponseMock, shop);
        verify(shareholderMappingService).updateShareholderMapping(createAccountHolderResponseMock, miraklShopUS);
        verify(shareholderMappingService).updateShareholderMapping(createAccountHolderResponseMock, miraklShopUK);

        List<ShareholderContact> shareHolders = createAccountHolderRequestCaptor.getAllValues()
                .stream()
                .map(CreateAccountHolderRequest::getAccountHolderDetails)
                .map(AccountHolderDetails::getBusinessDetails)
                .map(BusinessDetails::getShareholders)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        Assertions.assertThat(shareHolders).containsExactlyInAnyOrder(shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4, shareHolderMockUS, shareHolderMockUK);

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
        miraklContactInformation.setState("state");
        return miraklContactInformation;
    }

    private void setup(List<MiraklAdditionalFieldValue> additionalFields) throws Exception {
        MiraklShops miraklShops = new MiraklShops();
        List<MiraklShop> shops = new ArrayList<>();
        miraklShops.setShops(shops);
        miraklShops.setTotalCount(2L);

        shops.add(GENERIC_SHOP_INDEX, shop);
        shops.add(US_SHOP_INDEX, miraklShopUS);
        shops.add(UK_SHOP_INDEX, miraklShopUK);

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

        miraklShopUS.setContactInformation(contactInformation);
        miraklShopUS.setAdditionalFieldValues(additionalFields);
        miraklShopUS.setId("1");
        miraklShopUS.setCurrencyIsoCode(MiraklIsoCurrencyCode.USD);
        miraklShopUS.setPaymentInformation(createMiraklAbaBankAccountInformation());

        miraklShopUK.setContactInformation(contactInformation);
        miraklShopUK.setAdditionalFieldValues(additionalFields);
        miraklShopUK.setId("2");
        miraklShopUK.setCurrencyIsoCode(MiraklIsoCurrencyCode.GBP);
        miraklShopUK.setPaymentInformation(createMiraklUkBankAccountInformation());

        when(miraklMarketplacePlatformOperatorApiClientMock.getShops(any())).thenReturn(miraklShops);
        when(adyenAccountServiceMock.getAccountHolder(any())).thenReturn(getAccountHolderResponseMock);
    }

    private MiraklAbaBankAccountInformation createMiraklAbaBankAccountInformation() {
        MiraklAbaBankAccountInformation miraklAbaBankAccountInformation = new MiraklAbaBankAccountInformation();
        miraklAbaBankAccountInformation.setBankAccountNumber("1234567890");
        miraklAbaBankAccountInformation.setRoutingNumber("121000358");
        miraklAbaBankAccountInformation.setBankCity("Amsterdam");
        miraklAbaBankAccountInformation.setBankName("TestBank");
        miraklAbaBankAccountInformation.setBankStreet("street");
        miraklAbaBankAccountInformation.setBankZip("12345");
        miraklAbaBankAccountInformation.setOwner("owner");
        return miraklAbaBankAccountInformation;
    }

    private MiraklUkBankAccountInformation createMiraklUkBankAccountInformation() {
        MiraklUkBankAccountInformation miraklUkBankAccountInformation = new MiraklUkBankAccountInformation();
        miraklUkBankAccountInformation.setBankAccountNumber("75849855");
        miraklUkBankAccountInformation.setBankSortCode("200052");
        miraklUkBankAccountInformation.setBankCity("PASSED");
        miraklUkBankAccountInformation.setBankName("PASSED");
        miraklUkBankAccountInformation.setBankStreet("street");
        miraklUkBankAccountInformation.setBankZip("12345");
        miraklUkBankAccountInformation.setOwner("TestData");
        return miraklUkBankAccountInformation;
    }
}

