package com.hpe.cap_rotation_balance.domain.specification;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import org.springframework.data.jpa.domain.Specification;

/**
 * SalesOrderSpec — v4
 * Added: byHeaderStatus() to support the new Header Status filter.
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
     * NEW — Filter by SAP Header Status (e.g. "OPN", "INV", "CANC").
     * Case-insensitive to tolerate mixed-case values coming from SAP exports.
     */
    public static Specification<SalesOrder> byHeaderStatus(String headerStatus) {
        return (root, query, cb) ->
                headerStatus == null || headerStatus.isBlank()
                        ? cb.conjunction()
                        : cb.equal(cb.upper(root.get("headerStatus")), headerStatus.toUpperCase());
    }

    /**
     * Orders without a price yet (LOADED + orderValue IS NULL).
     * Used by stat cards.
     */
    public static Specification<SalesOrder> pricePending() {
        return (root, query, cb) ->
                cb.and(
                        cb.equal(cb.upper(root.get("internalStatus")), "LOADED"),
                        cb.isNull(root.get("orderValue"))
                );
    }
}