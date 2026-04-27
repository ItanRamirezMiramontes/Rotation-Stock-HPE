package com.hpe.cap_rotation_balance.domain.repository;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SalesOrderRepository — v4
 * Added: findDistinctHeaderStatuses() to feed the new Header Status filter dropdown.
 */
@Repository
public interface SalesOrderRepository
        extends JpaRepository<SalesOrder, String>,
        JpaSpecificationExecutor<SalesOrder> {

    List<SalesOrder> findByCustomer_CustomerId(String customerId);

    List<SalesOrder> findTop10ByOrderByUpdatedAtDesc();

    long countByInternalStatus(String internalStatus);

    @Query("SELECT DISTINCT s.omRegion FROM SalesOrder s WHERE s.omRegion IS NOT NULL ORDER BY s.omRegion")
    List<String> findDistinctRegions();

    @Query("SELECT DISTINCT s.fiscalQuarter FROM SalesOrder s WHERE s.fiscalQuarter IS NOT NULL ORDER BY s.fiscalQuarter")
    List<String> findDistinctFiscalQuarters();

    @Query("SELECT DISTINCT s.fiscalYear FROM SalesOrder s WHERE s.fiscalYear IS NOT NULL ORDER BY s.fiscalYear DESC")
    List<Integer> findDistinctFiscalYears();

    /**
     * NEW — Distinct SAP Header Status values (e.g. "OPN", "INV", "CANC").
     * Populates the Header Status dropdown in the Orders filter bar.
     */
    @Query("SELECT DISTINCT s.headerStatus FROM SalesOrder s WHERE s.headerStatus IS NOT NULL ORDER BY s.headerStatus")
    List<String> findDistinctHeaderStatuses();
}