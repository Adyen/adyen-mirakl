package com.adyen.mirakl.service;

import com.adyen.mirakl.AdyenMiraklConnectorApp;
import com.adyen.mirakl.domain.ShareholderMapping;
import com.adyen.mirakl.repository.ShareholderMappingRepository;
import com.adyen.model.marketpay.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AdyenMiraklConnectorApp.class)
@Transactional
public class ShareholderMappingServiceTest {

    private static final String SHOP_ID = "shopId";
    private static final String SHARE_HOLDER_CODE_1 = "shareHolderCode1";
    private static final String SHARE_HOLDER_CODE_2 = "shareHolderCode2";
    private static final String SHARE_HOLDER_CODE_3 = "shareHolderCode3";
    private static final String SHARE_HOLDER_CODE_4 = "shareHolderCode4";

    @Autowired
    private ShareholderMappingService shareholderMappingService;
    @Autowired
    private ShareholderMappingRepository shareholderMappingRepository;

    @Mock
    private AccountHolderDetails accountHolderDetailsResponseMock;
    @Mock
    private BusinessDetails businessDetailsResponseMock;
    @Mock
    private ShareholderContact shareHolderResponseMock1, shareHolderResponseMock2, shareHolderResponseMock3, shareHolderResponseMock4;
    @Mock
    private CreateAccountHolderResponse createAccountHolderResponse;
    @Mock
    private UpdateAccountHolderResponse updateAccountHolderResponse;
    @Mock
    private MiraklShop miraklShopMock;
    @MockBean
    private UboService uboServiceMock;

    @Before
    public void setup(){
        when(accountHolderDetailsResponseMock.getBusinessDetails()).thenReturn(businessDetailsResponseMock);
        when(businessDetailsResponseMock.getShareholders()).thenReturn(ImmutableList.of(shareHolderResponseMock1, shareHolderResponseMock2, shareHolderResponseMock3, shareHolderResponseMock4));
        when(shareHolderResponseMock1.getShareholderCode()).thenReturn(SHARE_HOLDER_CODE_1);
        when(shareHolderResponseMock2.getShareholderCode()).thenReturn(SHARE_HOLDER_CODE_2);
        when(shareHolderResponseMock3.getShareholderCode()).thenReturn(SHARE_HOLDER_CODE_3);
        when(shareHolderResponseMock4.getShareholderCode()).thenReturn(SHARE_HOLDER_CODE_4);
        when(uboServiceMock.extractUboNumbersFromShop(miraklShopMock)).thenReturn(ImmutableSet.of(1,2,3,4));
    }

    @Test
    public void shouldSaveNewShareholderMappingOnCreate() {
        when(createAccountHolderResponse.getAccountHolderDetails()).thenReturn(accountHolderDetailsResponseMock);
        when(createAccountHolderResponse.getAccountHolderCode()).thenReturn(SHOP_ID);

        shareholderMappingService.updateShareholderMapping(createAccountHolderResponse, miraklShopMock);

        assertMapping();
    }

    @Test
    public void shouldSaveNewShareholderMappingOnUpdate() {
        when(updateAccountHolderResponse.getAccountHolderDetails()).thenReturn(accountHolderDetailsResponseMock);
        when(updateAccountHolderResponse.getAccountHolderCode()).thenReturn(SHOP_ID);

        shareholderMappingService.updateShareholderMapping(updateAccountHolderResponse, miraklShopMock);

        assertMapping();
    }

    @Test
    public void shouldDoNothingIfNothingIfNoAccountHolderDetails() {
        when(updateAccountHolderResponse.getAccountHolderDetails()).thenReturn(null);

        shareholderMappingService.updateShareholderMapping(updateAccountHolderResponse, miraklShopMock);

        List<ShareholderMapping> all = shareholderMappingRepository.findAll();
        Assertions.assertThat(all.size()).isEqualTo(0);
    }

    @Test
    public void shouldDoNothingIfNoBusinessDetails() {
        when(updateAccountHolderResponse.getAccountHolderDetails()).thenReturn(accountHolderDetailsResponseMock);
        when(accountHolderDetailsResponseMock.getBusinessDetails()).thenReturn(null);

        shareholderMappingService.updateShareholderMapping(updateAccountHolderResponse, miraklShopMock);

        List<ShareholderMapping> all = shareholderMappingRepository.findAll();
        Assertions.assertThat(all.size()).isEqualTo(0);
    }


    @Test
    public void shouldDoNothingIfNoShareholders() {
        when(updateAccountHolderResponse.getAccountHolderDetails()).thenReturn(accountHolderDetailsResponseMock);
        when(accountHolderDetailsResponseMock.getBusinessDetails()).thenReturn(businessDetailsResponseMock);
        when(businessDetailsResponseMock.getShareholders()).thenReturn(null);

        shareholderMappingService.updateShareholderMapping(updateAccountHolderResponse, miraklShopMock);

        List<ShareholderMapping> all = shareholderMappingRepository.findAll();
        Assertions.assertThat(all.size()).isEqualTo(0);
    }

    private void assertMapping() {
        List<ShareholderMapping> all = shareholderMappingRepository.findAll();
        Assertions.assertThat(all.size()).isEqualTo(4);
        Assertions.assertThat(all.get(0).getMiraklShopId()).isEqualTo(SHOP_ID);
        Assertions.assertThat(all.get(0).getMiraklUboNumber()).isEqualTo(1);
        Assertions.assertThat(all.get(0).getAdyenShareholderCode()).isEqualTo(SHARE_HOLDER_CODE_1);
        Assertions.assertThat(all.get(1).getMiraklShopId()).isEqualTo(SHOP_ID);
        Assertions.assertThat(all.get(1).getMiraklUboNumber()).isEqualTo(2);
        Assertions.assertThat(all.get(1).getAdyenShareholderCode()).isEqualTo(SHARE_HOLDER_CODE_2);
        Assertions.assertThat(all.get(2).getMiraklShopId()).isEqualTo(SHOP_ID);
        Assertions.assertThat(all.get(2).getMiraklUboNumber()).isEqualTo(3);
        Assertions.assertThat(all.get(2).getAdyenShareholderCode()).isEqualTo(SHARE_HOLDER_CODE_3);
        Assertions.assertThat(all.get(3).getMiraklShopId()).isEqualTo(SHOP_ID);
        Assertions.assertThat(all.get(3).getMiraklUboNumber()).isEqualTo(4);
        Assertions.assertThat(all.get(3).getAdyenShareholderCode()).isEqualTo(SHARE_HOLDER_CODE_4);
    }

}
