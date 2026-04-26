package com.hpe.cap_rotation_balance.domain.repository;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de órdenes.
 *
 * Extiende JpaSpecificationExecutor para soportar filtros dinámicos
 * (región, quarter, cliente) sin proliferar métodos findByX.
 * El SalesOrderController usará Specification<SalesOrder> construidas
 * en SalesOrderSpec para componer los filtros recibidos como query params.
 */
@Repository
public interface SalesOrderRepository
        extends JpaRepository<SalesOrder, String>,
        JpaSpecificationExecutor<SalesOrder> {

    // Usado por CustomerController — sin cambios
    List<SalesOrder> findByCustomer_CustomerId(String customerId);

    // Auditoría — sin cambios
    List<SalesOrder> findTop10ByOrderByUpdatedAtDesc();

    // Conteo para las stat-cards del dashboard
    long countByInternalStatus(String internalStatus);

    // Regiones distintas disponibles — alimenta los dropdowns del frontend
    @Query("SELECT DISTINCT s.omRegion FROM SalesOrder s WHERE s.omRegion IS NOT NULL ORDER BY s.omRegion")
    List<String> findDistinctRegions();

    // Quarters fiscales distintos disponibles — alimenta los dropdowns del frontend
    @Query("SELECT DISTINCT s.fiscalQuarter FROM SalesOrder s WHERE s.fiscalQuarter IS NOT NULL ORDER BY s.fiscalQuarter")
    List<String> findDistinctFiscalQuarters();

    // Años fiscales distintos disponibles
    @Query("SELECT DISTINCT s.fiscalYear FROM SalesOrder s WHERE s.fiscalYear IS NOT NULL ORDER BY s.fiscalYear DESC")
    List<Integer> findDistinctFiscalYears();
}