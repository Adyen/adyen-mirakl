package com.adyen.mirakl.repository;

import com.adyen.mirakl.domain.DocError;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the DocError entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DocErrorRepository extends JpaRepository<DocError, Long> {

}
