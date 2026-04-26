package com.hpe.cap_rotation_balance.domain.specification;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import org.springframework.data.jpa.domain.Specification;

/**
 * Constructores de Specification<SalesOrder> para filtros dinámicos.
 *
 * Se componen con .and() en el Controller:
 *   Specification<SalesOrder> spec = Specification.where(null);
 *   if (region != null)  spec = spec.and(SalesOrderSpec.byRegion(region));
 *   if (quarter != null) spec = spec.and(SalesOrderSpec.byQuarter(quarter));
 *   ...
 *   repository.findAll(spec, pageable);
 *
 * Esto reemplaza la proliferación de métodos findByXAndY en el repositorio.
 */
public class SalesOrderSpec {

    private SalesOrderSpec() {}

    public static Specification<SalesOrder> byRegion(String region) {
        return (root, query, cb) ->
                region == null || region.isBlank()
                        ? cb.conjunction()
                        : cb.equal(cb.lower(root.get("omRegion")), region.toLowerCase());
    }

    public static Specification<SalesOrder> byFiscalQuarter(String quarter) {
        return (root, query, cb) ->
                quarter == null || quarter.isBlank()
                        ? cb.conjunction()
                        : cb.equal(cb.lower(root.get("fiscalQuarter")), quarter.toLowerCase());
    }

    public static Specification<SalesOrder> byFiscalYear(Integer year) {
        return (root, query, cb) ->
                year == null
                        ? cb.conjunction()
                        : cb.equal(root.get("fiscalYear"), year);
    }

    public static Specification<SalesOrder> byCustomerId(String customerId) {
        return (root, query, cb) ->
                customerId == null || customerId.isBlank()
                        ? cb.conjunction()
                        : cb.equal(root.get("customer").get("customerId"), customerId);
    }

    public static Specification<SalesOrder> byInternalStatus(String status) {
        return (root, query, cb) ->
                status == null || status.isBlank()
                        ? cb.conjunction()
                        : cb.equal(cb.upper(root.get("internalStatus")), status.toUpperCase());
    }

    /**
     * Órdenes que NO tienen precio asignado todavía (internalStatus = LOADED, orderValue IS NULL).
     * Usada por el dashboard para mostrar el conteo de "Pendientes de cruce".
     */
    public static Specification<SalesOrder> pricePending() {
        return (root, query, cb) ->
                cb.and(
                        cb.equal(cb.upper(root.get("internalStatus")), "LOADED"),
                        cb.isNull(root.get("orderValue"))
                );
    }
}