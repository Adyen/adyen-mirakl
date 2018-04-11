package com.adyen.mirakl.service;

import com.adyen.mirakl.config.Constants;
import com.adyen.mirakl.domain.DocError;
import com.adyen.mirakl.domain.DocRetry;
import com.adyen.mirakl.domain.ShareholderMapping;
import com.adyen.mirakl.repository.DocErrorRepository;
import com.adyen.mirakl.repository.DocRetryRepository;
import com.adyen.mirakl.repository.ShareholderMappingRepository;
import com.adyen.mirakl.service.dto.UboDocumentDTO;
import com.adyen.model.marketpay.*;
import com.adyen.service.Account;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.mirakl.client.mmp.domain.common.FileWrapper;
import com.mirakl.client.mmp.domain.shop.document.MiraklShopDocument;
import com.mirakl.client.mmp.operator.core.MiraklMarketplacePlatformOperatorApiClient;
import com.mirakl.client.mmp.request.shop.document.MiraklDeleteShopDocumentRequest;
import com.mirakl.client.mmp.request.shop.document.MiraklGetShopDocumentsRequest;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static com.google.common.io.Files.toByteArray;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DocServiceTest {
    @InjectMocks
    private DocService docService;

    @Mock
    private MiraklMarketplacePlatformOperatorApiClient miraklMarketplacePlatformOperatorApiClientMock;
    @Mock
    private Account adyenAccountServiceMock;
    @Mock
    private DeltaService deltaServiceMock;
    @Mock
    private UboService uboServiceMock;
    @Mock
    private MiraklShopDocument miraklShopDocumentMock;
    @Mock
    private FileWrapper fileWrapperMock;
    @Mock
    private UploadDocumentResponse responseMock;
    @Mock
    private UboDocumentDTO uboDocumentDTOMock;
    @Mock
    private ShareholderMappingRepository shareholderMappingRepositoryMock;
    @Mock
    private ShareholderMapping shareholderMappingMock;
    @Mock
    private MiraklShopDocument miraklShopDocumentMock1, miraklShopDocumentMock2, miraklShopDocumentMock3;
    @Mock
    private DocRetryRepository docRetryRepositoryMock;
    @Mock
    private DocErrorRepository docErrorRepositoryMock;
    @Mock
    private DocRetry docRetryMock1, docRetryMock2;
    @Captor
    private ArgumentCaptor<UploadDocumentRequest> uploadDocumentRequestCaptor;
    @Captor
    private ArgumentCaptor<MiraklGetShopDocumentsRequest> miraklGetShopDocumentsRequestCaptor;
    @Captor
    private ArgumentCaptor<MiraklDeleteShopDocumentRequest> miraklDeleteShopDocumentRequestCaptor;
    @Captor
    private ArgumentCaptor<DocRetry> docRetryCaptor;
    @Captor
    private ArgumentCaptor<DocError> docErrorCaptor;


    @Test
    public void testRetrieveBankproofAndUpload() throws Exception {

        FileWrapper fileWrapper = mock(FileWrapper.class);
        URL url = Resources.getResource("fileuploads/BankStatement.jpg");
        File file = new File(url.getPath());

        List<MiraklShopDocument> miraklShopDocumentList = new ArrayList<>();
        MiraklShopDocument fakeDocument = new MiraklShopDocument();
        fakeDocument.setFileName(file.getName());
        fakeDocument.setTypeCode(Constants.BANKPROOF);
        fakeDocument.setShopId("1234");
        fakeDocument.setId("docId");
        miraklShopDocumentList.add(fakeDocument);

        GetAccountHolderResponse getAccountHolderResponse = new GetAccountHolderResponse();
        BankAccountDetail bankAccountDetail = new BankAccountDetail();
        bankAccountDetail.setIban("ibansomething");
        bankAccountDetail.setBankAccountUUID("uuid");
        AccountHolderDetails accountHolderDetails = new AccountHolderDetails();
        accountHolderDetails.addBankAccountDetail(bankAccountDetail);
        getAccountHolderResponse.setAccountHolderDetails(accountHolderDetails);

        when(miraklMarketplacePlatformOperatorApiClientMock.getShopDocuments(any())).thenReturn(miraklShopDocumentList);
        when(miraklMarketplacePlatformOperatorApiClientMock.downloadShopsDocuments(any())).thenReturn(fileWrapper);
        when(fileWrapper.getFile()).thenReturn(file);
        when(fileWrapper.getFilename()).thenReturn(file.getName());
        when(adyenAccountServiceMock.getAccountHolder(any())).thenReturn(getAccountHolderResponse);
        when(adyenAccountServiceMock.uploadDocument(uploadDocumentRequestCaptor.capture())).thenReturn(responseMock);
        when(docRetryRepositoryMock.findOneByDocId("docId")).thenReturn(Optional.of(docRetryMock1));

        docService.processUpdatedDocuments();

        UploadDocumentRequest uploadDocumentRequest = uploadDocumentRequestCaptor.getValue();
        assertEquals("1234", uploadDocumentRequest.getAccountHolderCode());
        assertEquals("uuid", uploadDocumentRequest.getBankAccountUUID());
        assertEquals(file.getName(), uploadDocumentRequest.getDocumentDetail().getFilename());
        assertEquals(Base64.getEncoder().encodeToString(toByteArray(file)), uploadDocumentRequest.getDocumentContent());
        assertEquals(DocumentDetail.DocumentTypeEnum.BANK_STATEMENT, uploadDocumentRequest.getDocumentDetail().getDocumentType());
        verify(deltaServiceMock).getDocumentDelta();
    }

    @Test
    public void shouldProcessUboDocuments() throws Exception {
        URL url = Resources.getResource("fileuploads/BankStatement.jpg");
        File file = new File(url.getPath());

        when(miraklMarketplacePlatformOperatorApiClientMock.getShopDocuments(any())).thenReturn(ImmutableList.of(miraklShopDocumentMock));
        when(miraklShopDocumentMock.getTypeCode()).thenReturn("typeCode");
        when(miraklShopDocumentMock.getShopId()).thenReturn("shopId");

        when(uboServiceMock.extractUboDocuments(ImmutableList.of(miraklShopDocumentMock))).thenReturn(ImmutableList.of(uboDocumentDTOMock));
        when(uboDocumentDTOMock.getMiraklShopDocument()).thenReturn(miraklShopDocumentMock);
        when(uboDocumentDTOMock.getDocumentTypeEnum()).thenReturn(DocumentDetail.DocumentTypeEnum.ID_CARD);
        when(uboDocumentDTOMock.getShareholderCode()).thenReturn("shareholderCode");

        when(miraklMarketplacePlatformOperatorApiClientMock.downloadShopsDocuments(any())).thenReturn(fileWrapperMock);
        when(fileWrapperMock.getFile()).thenReturn(file);
        when(fileWrapperMock.getFilename()).thenReturn("fileName");
        when(adyenAccountServiceMock.uploadDocument(any())).thenReturn(responseMock);

        when(miraklShopDocumentMock.getId()).thenReturn("docId");
        when(docRetryRepositoryMock.findOneByDocId("docId")).thenReturn(Optional.of(docRetryMock1));

        docService.processUpdatedDocuments();

        verify(adyenAccountServiceMock).uploadDocument(uploadDocumentRequestCaptor.capture());
        UploadDocumentRequest uploadDocumentRequest = uploadDocumentRequestCaptor.getValue();
        Assertions.assertThat(uploadDocumentRequest.getDocumentDetail().getShareholderCode()).isEqualTo("shareholderCode");
        Assertions.assertThat(uploadDocumentRequest.getDocumentDetail().getAccountHolderCode()).isEqualTo("shopId");
        Assertions.assertThat(uploadDocumentRequest.getShareholderCode()).isEqualTo("shareholderCode");
        Assertions.assertThat(uploadDocumentRequest.getAccountHolderCode()).isEqualTo("shopId");

    }

    @Test
    public void shouldRemoveShareHolderMedia(){
        when(shareholderMappingRepositoryMock.findOneByAdyenShareholderCode("shareHolderCode")).thenReturn(Optional.of(shareholderMappingMock));
        when(shareholderMappingMock.getMiraklShopId()).thenReturn("miraklShopID");
        when(shareholderMappingMock.getMiraklUboNumber()).thenReturn(2);

        when(miraklMarketplacePlatformOperatorApiClientMock.getShopDocuments(miraklGetShopDocumentsRequestCaptor.capture())).thenReturn(ImmutableList.of(miraklShopDocumentMock1, miraklShopDocumentMock2, miraklShopDocumentMock3));
        //ignored as we're looking to delete only ubo 2 documents
        when(miraklShopDocumentMock1.getTypeCode()).thenReturn("adyen-ubo1-photoid");

        //will delete both these documents
        when(miraklShopDocumentMock2.getTypeCode()).thenReturn("adyen-ubo2-photoid");
        when(miraklShopDocumentMock2.getId()).thenReturn("ubo2DocId1");
        when(miraklShopDocumentMock3.getTypeCode()).thenReturn("adyen-ubo2-photoid-rear");
        when(miraklShopDocumentMock3.getId()).thenReturn("ubo2DocId2");

        docService.removeMiraklMediaForShareHolder("shareHolderCode");

        verify(miraklMarketplacePlatformOperatorApiClientMock, times(2)).deleteShopDocument(miraklDeleteShopDocumentRequestCaptor.capture());

        final MiraklGetShopDocumentsRequest getShopsRequest = miraklGetShopDocumentsRequestCaptor.getValue();
        Assertions.assertThat(getShopsRequest.getShopIds()).containsOnly("miraklShopID");

        final List<MiraklDeleteShopDocumentRequest> deleteRequests = miraklDeleteShopDocumentRequestCaptor.getAllValues();
        Assertions.assertThat(deleteRequests.get(0).getDocumentId()).isEqualTo("ubo2DocId1");
        Assertions.assertThat(deleteRequests.get(1).getDocumentId()).isEqualTo("ubo2DocId2");
    }

    @Test
    public void saveNewFailedDocWhenDoesNotAlreadyExist(){
        when(miraklMarketplacePlatformOperatorApiClientMock.getShopDocuments(any())).thenReturn(ImmutableList.of(miraklShopDocumentMock));
        when(miraklShopDocumentMock.getTypeCode()).thenReturn(Constants.BANKPROOF);
        when(miraklShopDocumentMock.getId()).thenReturn("docId");
        when(miraklShopDocumentMock.getShopId()).thenReturn("shopId");

        when(docRetryRepositoryMock.findOneByDocId("docId")).thenReturn(Optional.empty());

        docService.processUpdatedDocuments();

        verify(docRetryRepositoryMock).saveAndFlush(docRetryCaptor.capture());
        verify(docErrorRepositoryMock).saveAndFlush(docErrorCaptor.capture());

        final DocRetry docRetry = docRetryCaptor.getValue();
        final DocError docError = docErrorCaptor.getValue();

        Assertions.assertThat(docRetry.getDocId()).isEqualTo("docId");
        Assertions.assertThat(docRetry.getShopId()).isEqualTo("shopId");
        Assertions.assertThat(docRetry.getDocErrors()).containsOnly(docError);
        Assertions.assertThat(docRetry.getTimesFailed()).isOne();
        Assertions.assertThat(docError.getError()).isEqualTo("java.lang.NullPointerException");
        Assertions.assertThat(docError.getDocRetry()).isEqualTo(docRetry);
    }

    @Test
    public void addToExistingDocError(){
        when(miraklMarketplacePlatformOperatorApiClientMock.getShopDocuments(any())).thenReturn(ImmutableList.of(miraklShopDocumentMock));
        when(miraklShopDocumentMock.getTypeCode()).thenReturn(Constants.BANKPROOF);
        when(miraklShopDocumentMock.getId()).thenReturn("docId");
        when(miraklShopDocumentMock.getShopId()).thenReturn("shopId");

        when(docRetryRepositoryMock.findOneByDocId("docId")).thenReturn(Optional.of(docRetryMock1));

        docService.processUpdatedDocuments();

        verify(docErrorRepositoryMock).saveAndFlush(docErrorCaptor.capture());
        final DocError docError = docErrorCaptor.getValue();
        Assertions.assertThat(docError.getError()).isEqualTo("java.lang.NullPointerException");
        Assertions.assertThat(docError.getDocRetry()).isEqualTo(docRetryMock1);
    }

    @Test
    public void shouldFilterShopDocsById(){

        when(docRetryRepositoryMock.findAll()).thenReturn(ImmutableList.of(docRetryMock1));
        when(docRetryMock1.getDocId()).thenReturn("docId1");
        when(docRetryMock1.getShopId()).thenReturn("shopId1");

        when(miraklMarketplacePlatformOperatorApiClientMock.getShopDocuments(miraklGetShopDocumentsRequestCaptor.capture())).thenReturn(ImmutableList.of(miraklShopDocumentMock1, miraklShopDocumentMock2));

        when(miraklShopDocumentMock1.getId()).thenReturn("docId1");
        when(miraklShopDocumentMock1.getShopId()).thenReturn("shopId1");
        when(miraklShopDocumentMock2.getId()).thenReturn("docId2");
        when(miraklShopDocumentMock2.getShopId()).thenReturn("shopId2");

        when(docRetryRepositoryMock.findOneByDocId("docId1")).thenReturn(Optional.empty());

        docService.retryFailedDocuments();

        final MiraklGetShopDocumentsRequest requestToMirakl = miraklGetShopDocumentsRequestCaptor.getValue();
        Assertions.assertThat(requestToMirakl.getShopIds()).containsOnly("shopId1");

        verify(uboServiceMock).extractUboDocuments(ImmutableList.of(miraklShopDocumentMock1));
    }


}
