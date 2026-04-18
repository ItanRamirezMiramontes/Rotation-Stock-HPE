package com.hpe.cap_rotation_balance.features.ingestion.mapper;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.enums.*;
import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import com.hpe.cap_rotation_balance.features.rotation_logic.service.FiscalEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final FiscalEngine fiscalEngine;

    public void updateRawData(SalesOrder order, ExcelOrderDTO dto) {
        // 1. Fechas y Periodos Fiscales
        LocalDate entryDate = parseDate(dto.entryDate(), dto.sorg());
        if (entryDate != null) {
            order.setEntryDate(entryDate);
            order.setFiscalQuarter(fiscalEngine.calculateQuarter(entryDate));
            order.setFiscalYear(fiscalEngine.calculateFiscalYear(entryDate));
        }

        // 2. Identificadores Básicos
        order.setHpeOrderId(dto.orderId());
        order.setCustPoRef(dto.custPoRef());
        order.setOrderType(OrderType.fromString(dto.type()));
        order.setOrderReason(dto.orderReasonCode()); // Ahora usa el código de SAP (Z30, etc)

        // 3. Estructura de Ventas
        order.setOmRegion(dto.omRegion());
        order.setSorg(dto.sorg());
        order.setSalesOffice(dto.salesOffice());
        order.setSalesGroup(dto.salesGroup());

        // 4. Logística
        order.setRtm(dto.rtm());
        order.setShipToAddress(dto.shipToAddress());

        // 5. Status de Cabecera (Mapeo de INV, OPEN, CANC)
        order.setHeaderStatus(mapSapStatus(dto.headerStatus()));

        // 6. Moneda
        if (dto.currency() != null) {
            try { order.setCurrency(Currency.valueOf(dto.currency().toUpperCase())); }
            catch (Exception ignored) {}
        }

        // IMPORTANTE: netValueItem NO se asigna aquí para que permanezca null/0
        // hasta que se suba el Price Report.
    }

    private OrderStatus mapSapStatus(String status) {
        if (status == null) return OrderStatus.UNKNOWN;
        return switch (status.toUpperCase()) {
            case "INV", "FULLY INVOICED" -> OrderStatus.INV;
            case "OPEN", "PROCESSING" -> OrderStatus.OPEN;
            case "CANC", "CANCELLED" -> OrderStatus.CANC;
            default -> OrderStatus.UNKNOWN;
        };
    }

    private LocalDate parseDate(String raw, String sorg) {
        if (raw == null || raw.isBlank()) return null;
        String cleanRaw = raw.trim();

        if (cleanRaw.matches("\\d+(\\.\\d+)?")) {
            try {
                return LocalDate.of(1899, 12, 30).plusDays((long) Double.parseDouble(cleanRaw));
            } catch (Exception ignored) {}
        }

        boolean isNorthAmerica = (sorg != null && (sorg.startsWith("US") || sorg.startsWith("CA")));
        String[] patterns = isNorthAmerica
                ? new String[]{"M/d/yy", "MM/dd/yy", "M/d/yyyy", "MM/dd/yyyy", "yyyy-MM-dd"}
                : new String[]{"d/M/yy", "dd/MM/yy", "d/M/yyyy", "dd/MM/yyyy", "yyyy-MM-dd"};

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

    private BigDecimal parseNetValue(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.replaceAll("[^\\d.-]", ""));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}