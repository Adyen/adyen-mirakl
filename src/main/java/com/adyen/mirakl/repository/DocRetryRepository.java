package com.adyen.mirakl.repository;

import com.adyen.mirakl.domain.DocRetry;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;

import java.util.List;
import java.util.Optional;


/**
 * Spring Data JPA repository for the DocRetry entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DocRetryRepository extends JpaRepository<DocRetry, Long> {

    Optional<DocRetry> findOneByDocId(String docId);

    List<DocRetry> findByShopId(String shopId);

}
