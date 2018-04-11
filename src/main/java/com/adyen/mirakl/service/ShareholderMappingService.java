package com.adyen.mirakl.service;

import com.adyen.mirakl.domain.ShareholderMapping;
import com.adyen.mirakl.repository.ShareholderMappingRepository;
import com.adyen.model.marketpay.AccountHolderDetails;
import com.adyen.model.marketpay.CreateAccountHolderResponse;
import com.adyen.model.marketpay.ShareholderContact;
import com.adyen.model.marketpay.UpdateAccountHolderResponse;
import com.mirakl.client.mmp.domain.shop.MiraklShop;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Iterator;
import java.util.stream.Collectors;

@Service
public class ShareholderMappingService {

    @Resource
    private ShareholderMappingRepository shareholderMappingRepository;
    @Resource
    private UboService uboService;

    public void updateShareholderMapping(CreateAccountHolderResponse response, MiraklShop miraklShop) {
        AccountHolderDetails accountHolderDetails = response.getAccountHolderDetails();
        if (shareholdersDoNotExist(accountHolderDetails)) {
            return;
        }
        String shopCode = response.getAccountHolderCode();
        updateMapping(shopCode, accountHolderDetails, miraklShop);
    }

    public void updateShareholderMapping(UpdateAccountHolderResponse response, MiraklShop miraklShop) {
        AccountHolderDetails accountHolderDetails = response.getAccountHolderDetails();
        if (shareholdersDoNotExist(accountHolderDetails)) {
            return;
        }
        String shopCode = response.getAccountHolderCode();
        updateMapping(shopCode, accountHolderDetails, miraklShop);
    }

    private void updateMapping(String shopCode, AccountHolderDetails accountHolderDetails, MiraklShop miraklShop) {
        final Iterator<String> shareholderCodes = accountHolderDetails.getBusinessDetails().getShareholders().stream()
            .map(ShareholderContact::getShareholderCode).collect(Collectors.toList()).iterator();
        for (Integer uboNumber : uboService.extractUboNumbersFromShop(miraklShop)) {
            if(noShareholderCodesLeft(shareholderCodes)){
                continue;
            }
            final String adyenShareholderCode = shareholderCodes.next();
            if(mappingAlreadyExists(shopCode, uboNumber, adyenShareholderCode)){
                continue;
            }
            ShareholderMapping shareholderMapping = new ShareholderMapping();
            shareholderMapping.setMiraklUboNumber(uboNumber);
            shareholderMapping.setAdyenShareholderCode(adyenShareholderCode);
            shareholderMapping.setMiraklShopId(shopCode);
            shareholderMappingRepository.saveAndFlush(shareholderMapping);
        }
    }

    private boolean noShareholderCodesLeft(final Iterator<String> shareholderCodes) {
        return !shareholderCodes.hasNext();
    }

    private boolean mappingAlreadyExists(final String shopCode, final Integer uboNumber, final String shareholderCode) {
        return shareholderMappingRepository.findOneByMiraklShopIdAndMiraklUboNumber(shopCode, uboNumber).isPresent() || shareholderMappingRepository.findOneByAdyenShareholderCode(shareholderCode).isPresent();
    }

    private boolean shareholdersDoNotExist(AccountHolderDetails accountHolderDetails) {
        return accountHolderDetails == null || accountHolderDetails.getBusinessDetails() == null || CollectionUtils.isEmpty(accountHolderDetails.getBusinessDetails().getShareholders());
    }

}
