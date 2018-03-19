package com.adyen.mirakl.service;

import com.adyen.mirakl.startup.MiraklStartupValidator;
import com.adyen.model.Address;
import com.adyen.model.Name;
import com.adyen.model.marketpay.*;
import com.adyen.service.Account;
import com.google.common.collect.ImmutableList;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.common.currency.MiraklIsoCurrencyCode;
import com.mirakl.client.mmp.domain.shop.MiraklContactInformation;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.runners.MockitoJUnitRunner;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

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
    private UboService uboService;
    @Mock
    private ShareholderContact shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4;

    @Captor
    private ArgumentCaptor<CreateAccountHolderRequest> createAccountHolderRequestCaptor;
    @Captor
    private ArgumentCaptor<UpdateAccountHolderRequest> updateAccountHolderRequestCaptor;
    @Captor
    private ArgumentCaptor<MiraklGetShopsRequest> miraklGetShopsRequestCaptor;


    @Test
    public void testGetIso2CountryCode() {
        assertEquals("GB", shopService.getIso2CountryCodeFromIso3("GBR"));
    }


    @Test
    public void testIsIbanChanged() throws Exception {

        MiraklShop shop = new MiraklShop();
        shop.setId("id");
        MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = createMiraklIbanBankAccountInformation();
        shop.setPaymentInformation(miraklIbanBankAccountInformation);

        GetAccountHolderResponse getAccountHolderResponse = createGetAccountHolderResponse();

        // update bankaccount match
        assertEquals(false, shopService.isIbanChanged(getAccountHolderResponse, shop));

        // update bankAccountDetails to not matching one
        getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().get(0).setIban("GBDifferentIBAN");
        assertEquals(true, shopService.isIbanChanged(getAccountHolderResponse, shop));
    }

    @Test
    public void testIsIbanIdentical() throws Exception {
        String iban = "GB00IBAN";
        GetAccountHolderResponse getAccountHolderResponse = createGetAccountHolderResponse();
        assertEquals(true, shopService.isIbanIdentical(iban, getAccountHolderResponse));

        iban = "GB_IBAN_DOES_NOT_MATCH";
        assertEquals(false, shopService.isIbanIdentical(iban, getAccountHolderResponse));
    }

    @Test
    public void testDeleteBankAccountRequest() throws Exception {
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

        DeleteBankAccountRequest request = shopService.deleteBankAccountRequest(getAccountHolderResponse);
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
        Assertions.assertThat(address.getHouseNumberOrName()).isEqualTo("1");
        Assertions.assertThat(address.getPostalCode()).isEqualTo("zipCode");
        Assertions.assertThat(address.getStateOrProvince()).isEqualTo("state");
        Assertions.assertThat(address.getStreet()).isEqualTo("street");
        Assertions.assertThat(address.getCountry()).isEqualTo("GB");
        Assertions.assertThat(address.getCity()).isEqualTo("city");

        final List<BankAccountDetail> bankAccountDetails = request.getAccountHolderDetails().getBankAccountDetails();
        Assertions.assertThat(bankAccountDetails.size()).isEqualTo(1);
        final BankAccountDetail bankDetails = bankAccountDetails.iterator().next();
        Assertions.assertThat(bankDetails.getOwnerPostalCode()).isEqualTo("zipCode");
        Assertions.assertThat(bankDetails.getOwnerName()).isEqualTo("owner");
        Assertions.assertThat(bankDetails.getBankBicSwift()).isEqualTo("BIC");
        Assertions.assertThat(bankDetails.getCountryCode()).isEqualTo("GB");
        Assertions.assertThat(bankDetails.getOwnerHouseNumberOrName()).isEqualTo("1");
        Assertions.assertThat(bankDetails.getIban()).isEqualTo("GB00IBAN");
        Assertions.assertThat(bankDetails.getCurrencyCode()).isEqualTo("EUR");
        Assertions.assertThat(bankDetails.getBankCity()).isEqualTo("bankCity");
        Assertions.assertThat(individualDetails.getPersonalData().getDateOfBirth()).isEqualTo("1989-03-15T23:00:00Z");
    }

    @Test
    public void testRetrieveUpdatedShopsUpdate() throws Exception {
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(MiraklStartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE));
        additionalField.setValue(MiraklStartupValidator.AdyenLegalEntityType.INDIVIDUAL.toString());

        final ImmutableList<MiraklAdditionalFieldValue> additionalFields = new ImmutableList.Builder<MiraklAdditionalFieldValue>()
            .add(additionalField).build();
        setup(additionalFields);
        when(adyenAccountServiceMock.updateAccountHolder(updateAccountHolderRequestCaptor.capture())).thenReturn(updateAccountHolderResponseMock);
        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn("alreadyExisting");
        when(uboService.extractUbos(any(), any(), Matchers.eq(4))).thenReturn(ImmutableList.of(shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4));

        shopService.processUpdatedShops();

        UpdateAccountHolderRequest request = updateAccountHolderRequestCaptor.getValue();
        verify(adyenAccountServiceMock).updateAccountHolder(request);
        verify(shareholderMappingService).updateShareholderMapping(updateAccountHolderResponseMock);
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
        MiraklShop shop = new MiraklShop();
        shop.setId("id");
        shop.setCurrencyIsoCode(MiraklIsoCurrencyCode.EUR);


        MiraklContactInformation miraklContactInformation = createMiraklContactInformation();
        shop.setContactInformation(miraklContactInformation);

        MiraklIbanBankAccountInformation miraklIbanBankAccountInformation = createMiraklIbanBankAccountInformation();
        shop.setPaymentInformation(miraklIbanBankAccountInformation);


        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn(null);

        // Update with no IBAN yet
        UpdateAccountHolderRequest request = shopService.updateAccountHolderRequestFromShop(shop, getAccountHolderResponseMock);


        assertEquals("id", request.getAccountHolderCode());
        assertEquals("GB", request.getAccountHolderDetails().getBankAccountDetails().get(0).getCountryCode());
        assertEquals("owner", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerName());
        assertEquals("GB00IBAN", request.getAccountHolderDetails().getBankAccountDetails().get(0).getIban());
        assertEquals("BIC", request.getAccountHolderDetails().getBankAccountDetails().get(0).getBankBicSwift());
        assertEquals("1111AA", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerPostalCode());
        assertEquals("BIC", request.getAccountHolderDetails().getBankAccountDetails().get(0).getBankBicSwift());
        assertEquals("1", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerHouseNumberOrName());



        // Update with the same IBAN
        GetAccountHolderResponse getAccountHolderResponse = createGetAccountHolderResponse();

        UpdateAccountHolderRequest requestWithoutIbanChange = shopService.updateAccountHolderRequestFromShop(shop, getAccountHolderResponse);
        Assertions.assertThat(requestWithoutIbanChange.getAccountHolderDetails().getBankAccountDetails()).isEmpty();


        // Update with a different IBAN
        getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().get(0).setIban("GBDIFFERENTIBAN");

        UpdateAccountHolderRequest requestWithIbanChange = shopService.updateAccountHolderRequestFromShop(shop, getAccountHolderResponse);
        assertEquals(1, requestWithIbanChange.getAccountHolderDetails().getBankAccountDetails().size());
        assertEquals("GB00IBAN", requestWithIbanChange.getAccountHolderDetails().getBankAccountDetails().get(0).getIban());
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
        when(uboService.extractUbos(any(), any(), Matchers.eq(4))).thenReturn(ImmutableList.of(shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4));

        shopService.processUpdatedShops();

        verify(deltaService).updateShopDelta(any(ZonedDateTime.class));
        verify(shareholderMappingService).updateShareholderMapping(createAccountHolderResponseMock);

        List<ShareholderContact> shareHolders = createAccountHolderRequestCaptor.getAllValues()
            .stream()
            .map(CreateAccountHolderRequest::getAccountHolderDetails)
            .map(AccountHolderDetails::getBusinessDetails)
            .map(BusinessDetails::getShareholders)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

        Assertions.assertThat(shareHolders).containsExactlyInAnyOrder(shareHolderMock1, shareHolderMock2, shareHolderMock3, shareHolderMock4);

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
        miraklContactInformation.setStreet1("1 street");
        miraklContactInformation.setZipCode("1111AA");
        miraklContactInformation.setCivility("Mrs");
        return miraklContactInformation;
    }

    private void setup(List<MiraklAdditionalFieldValue> additionalFields) throws Exception {
        MiraklShops miraklShops = new MiraklShops();
        List<MiraklShop> shops = new ArrayList<>();
        miraklShops.setShops(shops);
        miraklShops.setTotalCount(1L);

        MiraklShop shop = new MiraklShop();
        shops.add(shop);

        MiraklContactInformation contactInformation = new MiraklContactInformation();
        contactInformation.setEmail("email");
        contactInformation.setFirstname("firstName");
        contactInformation.setLastname("lastName");
        contactInformation.setCountry("GBR");
        contactInformation.setCivility("Mrs");
        contactInformation.setCity("city");
        contactInformation.setStreet1("street");
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

