package com.adyen.mirakl.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import com.adyen.constants.ErrorTypeCodes;
import com.adyen.model.marketpay.ErrorFieldType;
import com.google.common.collect.ImmutableList;
import com.mirakl.client.mmp.domain.shop.MiraklShop;

@Service
@Transactional
public class InvalidFieldsNotificationService {

    private final Logger log = LoggerFactory.getLogger(InvalidFieldsNotificationService.class);

    // error codes that are safe to be sent to seller
    private final static List<Integer> sellerErrorCodes = ImmutableList.of(ErrorTypeCodes.EMAIL_INVALID,
                                                                           ErrorTypeCodes.COUNTRY_INVALID,
                                                                           ErrorTypeCodes.CONTAINS_NUMBERS,
                                                                           ErrorTypeCodes.WEB_ADDRESS_INVALID,
                                                                           ErrorTypeCodes.INVALID_DATE_FORMAT,
                                                                           ErrorTypeCodes.DATE_OUT_OF_RANGE,
                                                                           ErrorTypeCodes.BANK_DETAILS_INVALID,
                                                                           ErrorTypeCodes.POSTAL_CODE_INVALID,
                                                                           ErrorTypeCodes.STATE_CODE_INVALID,
                                                                           ErrorTypeCodes.STATE_CODE_UNKNOWN,
                                                                           ErrorTypeCodes.PHONE_NUMBER_INVALID,
                                                                           ErrorTypeCodes.PHONE_NUMBER_TOO_SHORT,
                                                                           ErrorTypeCodes.COUNTRY_NOT_SUPPORTED,
                                                                           ErrorTypeCodes.INVALID_CURRENCY,
                                                                           ErrorTypeCodes.BANK_CODE_UNKNOWN);

    @Resource
    private MailTemplateService mailTemplateService;

    @Resource
    private MessageSource messageSource;

    public List<String> getErrorsFromInvalidFields(List<ErrorFieldType> invalidFields) {
        if (CollectionUtils.isEmpty(invalidFields)) {
            return new ArrayList<>();
        }

        return invalidFields.stream().map(this::errorFieldTypeToString).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public void handleErrorsInResponse(MiraklShop shop, List<ErrorFieldType> invalidFields) {
        List<ErrorFieldType> invalidFieldsForSeller = invalidFields.stream().filter(errorFieldType -> sellerErrorCodes.contains(errorFieldType.getErrorCode())).collect(Collectors.toList());
        List<ErrorFieldType> invalidFieldsForOperator = invalidFields.stream().filter(errorFieldType -> ! sellerErrorCodes.contains(errorFieldType.getErrorCode())).collect(Collectors.toList());

        List<String> sellerErrors = getErrorsFromInvalidFields(invalidFieldsForSeller);
        List<String> operatorErrors = getErrorsFromInvalidFields(invalidFieldsForOperator);

        if (! sellerErrors.isEmpty()) {
            // notify seller with specific message
            log.debug("Sending {} error(s) to Seller {}", sellerErrors.size(), shop.getId());
            mailTemplateService.sendSellerEmailWithErrors(shop, sellerErrors);
        }

        if (! operatorErrors.isEmpty()) {
            // notify operator
            log.debug("Sending {} error(s) to Operator, for shop: {}", operatorErrors.size(), shop.getId());
            mailTemplateService.sendOperatorEmailWithErrors(shop, operatorErrors);
        }
    }

    private String errorFieldTypeToString(ErrorFieldType errorFieldType) {
        StringBuilder sb = new StringBuilder();
        Locale locale = Locale.getDefault();

        final List<Integer> errorDescriptionCodes = ImmutableList.of(ErrorTypeCodes.EMAIL_INVALID,
                                                                     ErrorTypeCodes.COUNTRY_INVALID,
                                                                     ErrorTypeCodes.CONTAINS_NUMBERS,
                                                                     ErrorTypeCodes.WEB_ADDRESS_INVALID,
                                                                     ErrorTypeCodes.INVALID_DATE_FORMAT,
                                                                     ErrorTypeCodes.DATE_OUT_OF_RANGE,
                                                                     ErrorTypeCodes.POSTAL_CODE_INVALID,
                                                                     ErrorTypeCodes.STATE_CODE_INVALID,
                                                                     ErrorTypeCodes.STATE_CODE_UNKNOWN,
                                                                     ErrorTypeCodes.PHONE_NUMBER_INVALID,
                                                                     ErrorTypeCodes.PHONE_NUMBER_TOO_SHORT,
                                                                     ErrorTypeCodes.COUNTRY_NOT_SUPPORTED,
                                                                     ErrorTypeCodes.INVALID_CURRENCY,
                                                                     ErrorTypeCodes.BANK_CODE_UNKNOWN);

        if (errorDescriptionCodes.contains(errorFieldType.getErrorCode())) {
            return errorFieldType.getErrorDescription();
        }
        if (ErrorTypeCodes.BANK_DETAILS_INVALID.equals(errorFieldType.getErrorCode())) {
            return sb.append("Bank details are invalid: ").append(errorFieldType.getErrorDescription()).toString();
        }

        sb.append(errorFieldType.getErrorDescription());
        if (errorFieldType.getFieldType().getField() != null) {
            sb.append(": ");
            String field = errorFieldType.getFieldType().getField();
            try {
                // Use field translation
                field = messageSource.getMessage(field, null, locale);
            } catch (NoSuchMessageException e) {
                log.info("No translation found for field: {}", field);
                // Append the field name as is if no translation is available
            }
            sb.append(field);
        }
        return sb.toString();
    }

    public MailTemplateService getMailTemplateService() {
        return mailTemplateService;
    }

    public void setMailTemplateService(MailTemplateService mailTemplateService) {
        this.mailTemplateService = mailTemplateService;
    }
}
