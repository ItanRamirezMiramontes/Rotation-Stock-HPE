package com.hpe.cap_rotation_balance.features.ingestion.mapper;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.enums.*;
import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import com.hpe.cap_rotation_balance.features.rotation_logic.service.FiscalEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * Component responsible for mapping Raw Data from Excel DTOs to the SalesOrder Entity.
 * Handles complex data transformations such as Date parsing based on SORG regions
 * and Fiscal Calendar calculations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMapper {

    private final FiscalEngine fiscalEngine;

    /**
     * Updates an existing or new SalesOrder entity with data from the Raw Data Excel report.
     * * @param order The SalesOrder entity to be updated.
     * @param dto   The data transfer object containing raw Excel values.
     */
    public void updateRawData(SalesOrder order, ExcelOrderDTO dto) {
        // 1. Basic Identifiers (Trimming to ensure clean IDs for DB lookups)
        String cleanId = (dto.orderId() != null) ? dto.orderId().trim() : null;
        order.setHpeOrderId(cleanId);

        order.setCustPoRef(dto.custPoRef() != null ? dto.custPoRef().trim() : null);
        order.setOrderType(OrderType.fromString(dto.type()));
        order.setOrderReason(dto.orderReasonCode());

        // 2. Dates and Fiscal Periods
        LocalDate entryDate = parseDate(dto.entryDate(), dto.sorg());
        if (entryDate != null) {
            order.setEntryDate(entryDate);
            order.setFiscalQuarter(fiscalEngine.calculateQuarter(entryDate));
            order.setFiscalYear(fiscalEngine.calculateFiscalYear(entryDate));
        } else {
            log.warn("Could not parse date '{}' for order ID: {}", dto.entryDate(), cleanId);
        }

        // 3. Sales Structure
        order.setOmRegion(dto.omRegion());
        order.setSorg(dto.sorg());
        order.setSalesOffice(dto.salesOffice());
        order.setSalesGroup(dto.salesGroup());

        // 4. Logistics & Shipping
        order.setRtm(dto.rtm());
        order.setShipToAddress(dto.shipToAddress());

        // 5. Header Status Mapping
        order.setHeaderStatus(mapSapStatus(dto.headerStatus()));

        // 6. Currency Validation
        if (dto.currency() != null && !dto.currency().isBlank()) {
            try {
                order.setCurrency(Currency.valueOf(dto.currency().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.error("Unrecognized currency code: {}. Defaulting to null.", dto.currency());
            }
        }

        // Note: netValueItem is NOT modified here.
        // It is preserved until the Price Report ingestion process.
    }

    /**
     * Maps SAP Status strings (INV, OPN, CANC) to internal OrderStatus enum.
     */
    private OrderStatus mapSapStatus(String status) {
        if (status == null || status.isBlank()) return OrderStatus.UNKNOWN;
        String s = status.trim().toUpperCase();
        return switch (s) {
            case "INV", "FULLY INVOICED", "INVOICED" -> OrderStatus.INV;
            case "OPEN", "PROCESSING", "OPN" -> OrderStatus.OPEN;
            case "CANC", "CANCELLED", "CNCL" -> OrderStatus.CANC;
            default -> OrderStatus.UNKNOWN;
        };
    }

    /**
     * Sophisticated date parser that handles:
     * 1. Excel Serial Numbers (e.g., 45230).
     * 2. Regional formats (MM/DD/YYYY for US/CA, DD/MM/YYYY for others) based on SORG.
     */
    private LocalDate parseDate(String raw, String sorg) {
        if (raw == null || raw.isBlank()) return null;
        String cleanRaw = raw.trim();

        // Handle Excel Serial Dates (numeric strings)
        if (cleanRaw.matches("\\d+(\\.\\d+)?")) {
            try {
                return LocalDate.of(1899, 12, 30).plusDays((long) Double.parseDouble(cleanRaw));
            } catch (Exception ignored) {}
        }

        // Determine date logic based on Sales Organization (SORG)
        boolean isNorthAmerica = (sorg != null && (sorg.startsWith("US") || sorg.startsWith("CA")));

        String[] patterns = isNorthAmerica
                ? new String[]{"M/d/yy", "MM/dd/yy", "M/d/yyyy", "MM/dd/yyyy", "yyyy-MM-dd", "MM-dd-yyyy"}
                : new String[]{"d/M/yy", "dd/MM/yy", "d/M/yyyy", "dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy"};

        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                        .appendPattern(pattern)
                        .parseDefaulting(java.time.temporal.ChronoField.ERA, 1)
                        .toFormatter();
                return LocalDate.parse(cleanRaw, formatter);
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Utility to clean and parse monetary values from Excel strings.
     * Removes currency symbols, commas, and whitespace.
     */
    public BigDecimal parseNetValue(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            String cleaned = value.replaceAll("[^\\d.-]", "");
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            log.error("Error parsing monetary value: {}. Returning ZERO.", value);
            return BigDecimal.ZERO;
        }
    }
}