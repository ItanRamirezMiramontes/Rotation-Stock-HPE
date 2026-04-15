package com.hpe.cap_rotation_balance.features.ingestion.mapper;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.enums.*;
import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

@Slf4j
@Component
public class OrderMapper {

    public void updateRawData(SalesOrder order, ExcelOrderDTO dto) {
        // 1. FECHA (Arreglo '1/31/26')
        LocalDate entryDate = parseDate(dto.entryDate(), dto.sorg());
        if (entryDate != null) {
            order.setEntryDate(entryDate);
            order.setFiscalQuarter(calculateHpeQuarter(entryDate));
            order.setFiscalYear(calculateFiscalYear(entryDate));
        }

        // 2. IDENTIFICADORES (Solo actualiza si no es null en el DTO)
        if (dto.orderId() != null && !dto.orderId().isBlank()) order.setHpeOrderId(dto.orderId());
        if (dto.custPoRef() != null) order.setCustPoRef(dto.custPoRef());
        if (dto.omRegion() != null) order.setOmRegion(dto.omRegion());
        if (dto.sorg() != null) order.setSorg(dto.sorg());

        // 3. LOCALIZACIÓN (Sales Office y Sales Group)
        // Agregamos un log para ver qué llega exactamente del Excel para esa orden
        if ("7070070371".equals(dto.orderId())) {
            log.info("DEBUG ORDEN 371: Office del DTO: '{}', Group del DTO: '{}'",
                    dto.salesOffice(), dto.salesGroup());
        }

        // Cambiamos la lógica: Si viene nulo o vacío en el DTO,
        // pero el Excel SÍ tiene el dato, es que el Reader está fallando.
        // Si el DTO trae algo, lo seteamos siempre.
        if (dto.salesOffice() != null && !dto.salesOffice().isBlank()) {
            order.setSalesOffice(dto.salesOffice());
        } else {
            // Opcional: Si quieres que se guarde el null explícitamente si el Excel está vacío
            // order.setSalesOffice(null);
        }

        if (dto.salesGroup() != null && !dto.salesGroup().isBlank()) {
            order.setSalesGroup(dto.salesGroup());
        }

        // 4. ENUMS Y PRECIO
        order.setOrderReason("SAP Ingestion");
        order.setHeaderStatus(OrderStatus.fromString(dto.headerStatus()));
        order.setOrderType(OrderType.fromString(dto.type()));

        if (dto.currency() != null && !dto.currency().isBlank()) {
            try {
                order.setCurrency(Currency.valueOf(dto.currency().trim().toUpperCase()));
            } catch (Exception e) {
                log.warn("Moneda no válida: {}", dto.currency());
            }
        }

        BigDecimal rawPrice = parseNetValue(dto.netValue());
        if (order.getNetValueItem() == null || order.getNetValueItem().compareTo(BigDecimal.ZERO) == 0) {
            order.setNetValueItem(rawPrice);
        }
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

    private FiscalQuarter calculateHpeQuarter(LocalDate date) {
        if (date == null) return null;
        int m = date.getMonthValue();
        if (m == 11 || m == 12 || m == 1) return FiscalQuarter.Q1;
        if (m >= 2 && m <= 4) return FiscalQuarter.Q2;
        if (m >= 5 && m <= 7) return FiscalQuarter.Q3;
        return FiscalQuarter.Q4;
    }

    private Integer calculateFiscalYear(LocalDate date) {
        if (date == null) return null;
        return (date.getMonthValue() >= 11) ? date.getYear() + 1 : date.getYear();
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