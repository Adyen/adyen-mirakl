package com.adyen.mirakl.service;

import com.adyen.mirakl.config.ApplicationProperties;
import com.adyen.mirakl.domain.MiraklDelta;
import com.adyen.mirakl.domain.MiraklDocumentDelta;
import com.adyen.mirakl.repository.MiraklDeltaRepository;
import com.adyen.mirakl.repository.MiraklDocumentDeltaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

@Service
public class DeltaService {
    private final Logger log = LoggerFactory.getLogger(DeltaService.class);

    @Resource
    private ApplicationProperties applicationProperties;

    @Resource
    private MiraklDeltaRepository miraklDeltaRepository;

    @Resource
    private MiraklDocumentDeltaRepository miraklDocumentDeltaRepository;

    /**
     * Get shop delta
     * If doens't exist, create and return a new one using application.initialDeltaDaysBack property
     */
    public Date getShopDelta() {
        final Optional<MiraklDelta> firstByOrderByIdDesc = miraklDeltaRepository.findFirstByOrderByIdDesc();

        if (firstByOrderByIdDesc.isPresent()) {
            return Date.from(firstByOrderByIdDesc.get().getShopDelta().toInstant());
        }

        log.info("No shopDelta found");
        ZonedDateTime defaultDate = ZonedDateTime.now().minusDays(applicationProperties.getInitialDeltaDaysBack());
        createNewShopDelta(defaultDate);
        return Date.from(defaultDate.toInstant());
    }

    private void createNewShopDelta(ZonedDateTime delta) {
        log.debug("Creating new shopDelta");
        final MiraklDelta miraklDelta = new MiraklDelta();
        miraklDelta.setShopDelta(delta);
        miraklDeltaRepository.saveAndFlush(miraklDelta);
    }

    public void updateShopDelta(ZonedDateTime delta) {
        MiraklDelta entity = miraklDeltaRepository.findFirstByOrderByIdDesc().orElseThrow(() -> new IllegalStateException("No shopDelta found"));
        entity.setShopDelta(delta);
        miraklDeltaRepository.saveAndFlush(entity);
    }

    /**
     * Get document delta
     * If doens't exist, create and return a new one using application.initialDeltaDaysBack property
     */
    public Date getDocumentDelta() {
        final Optional<MiraklDocumentDelta> firstByOrderByIdDesc = miraklDocumentDeltaRepository.findFirstByOrderByIdDesc();

        if (firstByOrderByIdDesc.isPresent()) {
            return Date.from(firstByOrderByIdDesc.get().getDocumentDelta().toInstant());
        }

        log.info("No documentDelta found");
        ZonedDateTime defaultDate = ZonedDateTime.now().minusDays(applicationProperties.getInitialDeltaDaysBack());
        createNewDocumentDelta(defaultDate);
        return Date.from(defaultDate.toInstant());
    }

    private void createNewDocumentDelta(ZonedDateTime delta) {
        log.debug("Creating new documentDelta");
        final MiraklDocumentDelta miraklDelta = new MiraklDocumentDelta();
        miraklDelta.setDocumentDelta(delta);
        miraklDocumentDeltaRepository.saveAndFlush(miraklDelta);
    }

    public void updateDocumentDelta(ZonedDateTime delta) {
        MiraklDocumentDelta entity = miraklDocumentDeltaRepository.findFirstByOrderByIdDesc().orElseThrow(() -> new IllegalStateException("No DocumentDelta found"));
        entity.setDocumentDelta(delta);
        miraklDocumentDeltaRepository.saveAndFlush(entity);
    }
}
