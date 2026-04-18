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

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMapper {

    private final FiscalEngine fiscalEngine;

    public void updateRawData(SalesOrder order, ExcelOrderDTO dto) {
        // 1. Identificadores Básicos (IMPORTANTE: Limpiar el ID)
        String cleanId = (dto.orderId() != null) ? dto.orderId().trim() : null;
        order.setHpeOrderId(cleanId);

        order.setCustPoRef(dto.custPoRef() != null ? dto.custPoRef().trim() : null);
        order.setOrderType(OrderType.fromString(dto.type()));
        order.setOrderReason(dto.orderReasonCode());

        // 2. Fechas y Periodos Fiscales
        LocalDate entryDate = parseDate(dto.entryDate(), dto.sorg());
        if (entryDate != null) {
            order.setEntryDate(entryDate);
            order.setFiscalQuarter(fiscalEngine.calculateQuarter(entryDate));
            order.setFiscalYear(fiscalEngine.calculateFiscalYear(entryDate));
        } else {
            log.warn("No se pudo parsear la fecha '{}' para la orden {}", dto.entryDate(), cleanId);
        }

        // 3. Estructura de Ventas
        order.setOmRegion(dto.omRegion());
        order.setSorg(dto.sorg());
        order.setSalesOffice(dto.salesOffice());
        order.setSalesGroup(dto.salesGroup());

        // 4. Logística
        order.setRtm(dto.rtm());
        order.setShipToAddress(dto.shipToAddress());

        // 5. Status de Cabecera
        order.setHeaderStatus(mapSapStatus(dto.headerStatus()));

        // 6. Moneda (Validación robusta)
        if (dto.currency() != null && !dto.currency().isBlank()) {
            try {
                order.setCurrency(Currency.valueOf(dto.currency().trim().toUpperCase()));
            } catch (Exception e) {
                log.error("Moneda no reconocida: {}", dto.currency());
                // Opcional: poner una moneda por defecto o dejar null
            }
        }

        // NOTA: netValueItem NO se toca aquí.
        // Se mantiene lo que ya tenga la entidad (null si es nueva,
        // o el precio previo si es una actualización de RAW).
    }

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

    private LocalDate parseDate(String raw, String sorg) {
        if (raw == null || raw.isBlank()) return null;
        String cleanRaw = raw.trim();

        // Manejo de fechas seriales de Excel (ej: 45230)
        if (cleanRaw.matches("\\d+(\\.\\d+)?")) {
            try {
                return LocalDate.of(1899, 12, 30).plusDays((long) Double.parseDouble(cleanRaw));
            } catch (Exception ignored) {}
        }

        // Determinar orden de fecha basado en SORG
        boolean isNorthAmerica = (sorg != null && (sorg.startsWith("US") || sorg.startsWith("CA")));

        // Agregamos formatos con guiones y slash para mayor cobertura
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
     * Utilidad para limpiar montos monetarios de Excel que vienen como String
     */
    public BigDecimal parseNetValue(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            // Elimina símbolos de moneda, comas de miles y espacios
            String cleaned = value.replaceAll("[^\\d.-]", "");
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            log.error("Error parseando valor monetario: {}", value);
            return BigDecimal.ZERO;
        }
    }
}