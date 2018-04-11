package com.adyen.mirakl.repository;

import com.adyen.mirakl.domain.MiraklVoucherEntry;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the MiraklVoucherEntry entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MiraklVoucherEntryRepository extends JpaRepository<MiraklVoucherEntry, Long> {

}
