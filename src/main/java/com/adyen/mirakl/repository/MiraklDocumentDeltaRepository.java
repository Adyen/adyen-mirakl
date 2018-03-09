package com.adyen.mirakl.repository;

import com.adyen.mirakl.domain.MiraklDocumentDelta;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the MiraklDocumentDelta entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MiraklDocumentDeltaRepository extends JpaRepository<MiraklDocumentDelta, Long> {

}
