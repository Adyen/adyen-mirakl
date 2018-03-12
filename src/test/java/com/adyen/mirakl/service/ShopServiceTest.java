package com.adyen.mirakl.service;

import java.io.File;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import com.adyen.mirakl.startup.MiraklStartupValidator;
import com.adyen.model.Address;
import com.adyen.model.Name;
import com.adyen.model.marketpay.AccountHolderDetails;
import com.adyen.model.marketpay.BankAccountDetail;
import com.adyen.model.marketpay.BusinessDetails;
import com.adyen.model.marketpay.CreateAccountHolderRequest;
import com.adyen.model.marketpay.DeleteBankAccountRequest;
import com.adyen.model.marketpay.DocumentDetail;
import com.adyen.model.marketpay.GetAccountHolderResponse;
import com.adyen.model.marketpay.IndividualDetails;
import com.adyen.model.marketpay.ShareholderContact;
import com.adyen.model.marketpay.UpdateAccountHolderRequest;
import com.adyen.model.marketpay.UploadDocumentRequest;
import com.adyen.service.Account;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.mirakl.client.mmp.domain.common.FileWrapper;
import com.mirakl.client.mmp.domain.common.MiraklAdditionalFieldValue;
import com.mirakl.client.mmp.domain.common.currency.MiraklIsoCurrencyCode;
import com.mirakl.client.mmp.domain.shop.MiraklContactInformation;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import com.mirakl.client.mmp.domain.shop.MiraklShops;
import com.mirakl.client.mmp.domain.shop.bank.MiraklIbanBankAccountInformation;
import com.mirakl.client.mmp.domain.shop.document.MiraklShopDocument;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.request.shop.MiraklGetShopsRequest;
import com.mirakl.client.mmp.request.shop.document.MiraklGetShopDocumentsRequest;
import static com.google.common.io.Files.toByteArray;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
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

    @Captor
    private ArgumentCaptor<CreateAccountHolderRequest> createAccountHolderRequestCaptor;
    @Captor
    private ArgumentCaptor<UpdateAccountHolderRequest> updateAccountHolderRequestCaptor;
    @Captor
    private ArgumentCaptor<MiraklGetShopsRequest> miraklGetShopsRequestCaptor;
    @Captor
    private ArgumentCaptor<UploadDocumentRequest> uploadDocumentRequestCaptor;
    @Captor
    private ArgumentCaptor<MiraklGetShopDocumentsRequest> miraklGetShopDocumentsRequestArgumentCaptor;


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
        List<BankAccountDetail> bankAccountDetails = new ArrayList<BankAccountDetail>();
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

        shopService.retrieveUpdatedShops();
        verify(adyenAccountServiceMock, never()).createAccountHolder(any());
    }

    @Test
    public void testRetrieveUpdatedShopsCreate() throws Exception {
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(MiraklStartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE));
        additionalField.setValue(MiraklStartupValidator.AdyenLegalEntityType.INDIVIDUAL.toString());
        setup(MiraklStartupValidator.AdyenLegalEntityType.INDIVIDUAL, ImmutableList.of(additionalField));
        when(adyenAccountServiceMock.createAccountHolder(createAccountHolderRequestCaptor.capture())).thenReturn(null);
        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn("");

        shopService.retrieveUpdatedShops();
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

    }

    @Test
    public void testRetrieveUpdatedShopsUpdate() throws Exception {
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalField = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalField.setCode(String.valueOf(MiraklStartupValidator.CustomMiraklFields.ADYEN_LEGAL_ENTITY_TYPE));
        additionalField.setValue(MiraklStartupValidator.AdyenLegalEntityType.INDIVIDUAL.toString());
        setup(MiraklStartupValidator.AdyenLegalEntityType.INDIVIDUAL, ImmutableList.of(additionalField));
        when(adyenAccountServiceMock.updateAccountHolder(updateAccountHolderRequestCaptor.capture())).thenReturn(null);
        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn("alreadyExisting");

        shopService.retrieveUpdatedShops();
        UpdateAccountHolderRequest request = updateAccountHolderRequestCaptor.getValue();

        verify(adyenAccountServiceMock).updateAccountHolder(request);

        assertEquals("id", request.getAccountHolderCode());
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
        Optional<UpdateAccountHolderRequest> requestOptional = shopService.updateAccountHolderRequestFromShop(shop, getAccountHolderResponseMock);
        Assertions.assertThat(requestOptional.isPresent()).isTrue();
        requestOptional.ifPresent(request -> {
            assertEquals("id", request.getAccountHolderCode());
            assertEquals("GB", request.getAccountHolderDetails().getBankAccountDetails().get(0).getCountryCode());
            assertEquals("owner", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerName());
            assertEquals("GB00IBAN", request.getAccountHolderDetails().getBankAccountDetails().get(0).getIban());
            assertEquals("BIC", request.getAccountHolderDetails().getBankAccountDetails().get(0).getBankBicSwift());
            assertEquals("1111AA", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerPostalCode());
            assertEquals("BIC", request.getAccountHolderDetails().getBankAccountDetails().get(0).getBankBicSwift());
            assertEquals("1", request.getAccountHolderDetails().getBankAccountDetails().get(0).getOwnerHouseNumberOrName());
        });


        // Update with the same IBAN
        GetAccountHolderResponse getAccountHolderResponse = createGetAccountHolderResponse();

        Optional<UpdateAccountHolderRequest> requestWithoutIbanChange = shopService.updateAccountHolderRequestFromShop(shop, getAccountHolderResponse);
        Assertions.assertThat(requestWithoutIbanChange.isPresent()).isEqualTo(false);


        // Update with a different IBAN
        getAccountHolderResponse.getAccountHolderDetails().getBankAccountDetails().get(0).setIban("GBDIFFERENTIBAN");

        Optional<UpdateAccountHolderRequest> requestWithIbanChangeOptional = shopService.updateAccountHolderRequestFromShop(shop, getAccountHolderResponse);
        Assertions.assertThat(requestWithIbanChangeOptional.isPresent()).isEqualTo(true);
        requestWithIbanChangeOptional.ifPresent(requestWithIbanChange -> {
            assertEquals(1, requestWithIbanChange.getAccountHolderDetails().getBankAccountDetails().size());
            assertEquals("GB00IBAN", requestWithIbanChange.getAccountHolderDetails().getBankAccountDetails().get(0).getIban());
        });
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


        List<MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue> ubo1 = createMiraklAdditionalUboField("1");
        List<MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue> ubo2 = createMiraklAdditionalUboField("2");
        List<MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue> ubo3 = createMiraklAdditionalUboField("3");
        List<MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue> ubo4 = createMiraklAdditionalUboField("4");


        List<MiraklAdditionalFieldValue> addtionalFields = ImmutableList.of(ubo1, ubo2, ubo3, ubo4, ImmutableList.of(additionalField))
                                                                        .stream()
                                                                        .flatMap(Collection::stream)
                                                                        .collect(Collectors.toList());
        setup(MiraklStartupValidator.AdyenLegalEntityType.BUSINESS, addtionalFields);

        when(adyenAccountServiceMock.createAccountHolder(createAccountHolderRequestCaptor.capture())).thenReturn(null);
        when(getAccountHolderResponseMock.getAccountHolderCode()).thenReturn("");

        shopService.retrieveUpdatedShops();

        verify(deltaService).createNewShopDelta(any(ZonedDateTime.class));

        List<ShareholderContact> shareHolders = createAccountHolderRequestCaptor.getAllValues()
                                                                                .stream()
                                                                                .map(CreateAccountHolderRequest::getAccountHolderDetails)
                                                                                .map(AccountHolderDetails::getBusinessDetails)
                                                                                .map(BusinessDetails::getShareholders)
                                                                                .flatMap(Collection::stream)
                                                                                .collect(Collectors.toList());

        Set<String> firstNames = shareHolders.stream().map(ShareholderContact::getName).map(Name::getFirstName).collect(Collectors.toSet());
        Set<String> lastNames = shareHolders.stream().map(ShareholderContact::getName).map(Name::getLastName).collect(Collectors.toSet());
        Set<Name.GenderEnum> genders = shareHolders.stream().map(ShareholderContact::getName).map(Name::getGender).collect(Collectors.toSet());
        Set<String> emails = shareHolders.stream().map(ShareholderContact::getEmail).collect(Collectors.toSet());

        Assertions.assertThat(firstNames).containsExactlyInAnyOrder("firstname1", "firstname2", "firstname3", "firstname4");
        Assertions.assertThat(lastNames).containsExactlyInAnyOrder("lastname1", "lastname2", "lastname3", "lastname4");
        Assertions.assertThat(emails).containsExactlyInAnyOrder("email1", "email2", "email3", "email4");
        Assertions.assertThat(genders).containsOnly(Name.GenderEnum.UNKNOWN);

    }

    private List<MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue> createMiraklAdditionalUboField(String uboNumber) {
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalFieldUboCivility = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalFieldUboCivility.setValue("firstname" + uboNumber);
        additionalFieldUboCivility.setCode("adyen-ubo" + uboNumber + "-firstname");
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalFieldUboFirstName = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalFieldUboFirstName.setValue("lastname" + uboNumber);
        additionalFieldUboFirstName.setCode("adyen-ubo" + uboNumber + "-lastname");
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalFieldUboLastName = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalFieldUboLastName.setValue("gender" + uboNumber);
        additionalFieldUboLastName.setCode("adyen-ubo" + uboNumber + "-civility");
        MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue additionalFieldUboDob = new MiraklAdditionalFieldValue.MiraklValueListAdditionalFieldValue();
        additionalFieldUboDob.setValue("email" + uboNumber);
        additionalFieldUboDob.setCode("adyen-ubo" + uboNumber + "-email");
        return ImmutableList.of(additionalFieldUboCivility, additionalFieldUboFirstName, additionalFieldUboLastName, additionalFieldUboDob);
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

    private void setup(MiraklStartupValidator.AdyenLegalEntityType type, List<MiraklAdditionalFieldValue> additionalFields) throws Exception {
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

