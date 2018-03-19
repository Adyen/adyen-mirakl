package com.adyen.mirakl.scheduling;


import com.adyen.mirakl.service.DocService;
import com.adyen.mirakl.service.RetryEmailService;
import com.adyen.mirakl.service.ShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Profile({"dev", "prod"})
public class SchedulerTrigger {

    private static final Logger log = LoggerFactory.getLogger(SchedulerTrigger.class);

    @Resource
    private ShopService shopService;

    @Resource
    private DocService docService;

    @Resource
    private RetryEmailService retryEmailService;

    @Scheduled(cron = "${application.miraklPullCron}")
    public void runShopUpdates() {
        log.debug("Pulling shops from Mirakl");
        shopService.processUpdatedShops();
        log.debug("Pulling documents from Mirakl");
        docService.retrieveBankproofAndUpload();
    }

    @Scheduled(cron = "${application.emailRetryCron}")
    public void retryEmails() {
        retryEmailService.retryFailedEmails();
    }

    @Scheduled(cron = "${application.removeSentEmailsCron}")
    public void removeSentEmails() {
        retryEmailService.removeSentEmails();
    }

    public void setShopService(final ShopService shopService) {
        this.shopService = shopService;
    }

    public void setDocService(DocService docService) {
        this.docService = docService;
    }

}
