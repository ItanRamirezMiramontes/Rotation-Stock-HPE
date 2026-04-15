package com.hpe.cap_rotation_balance.features.ingestion.mapper;

import com.hpe.cap_rotation_balance.domain.entity.Customer;
import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.enums.Currency;
import com.hpe.cap_rotation_balance.domain.enums.OrderStatus;
import com.hpe.cap_rotation_balance.domain.enums.OrderType;
import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import com.hpe.cap_rotation_balance.domain.enums.FiscalQuarter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class OrderMapper {

    public SalesOrder toEntity(ExcelOrderDTO dto) {
        LocalDate entryDate = parseDate(dto.entryDate(), dto.sorg());

        // Instanciar el cliente para evitar el NullPointerException
        Customer customer = Customer.builder()
                .customerId(dto.soldToParty())
                .customerName("Region: " + dto.omRegion())
                .build();

        //FUNCIONES STREAM
        return SalesOrder.builder()
                .hpeOrderId(dto.orderId())
                .headerStatus(OrderStatus.valueOf(dto.headerStatus()))
                .omRegion(dto.omRegion())
                .sorg(dto.sorg())
                .salesOffice(dto.salesOffice())
                .salesGroup(dto.salesGroup())
                .orderType(OrderType.fromString(dto.type()))
                .entryDate(entryDate)
                .custPoRef(dto.custPoRef())
                .shipToAddress(dto.shipToAddress())
                .rtm(dto.rtm())
                .currency(Currency.valueOf(dto.currency()))
                .netValueItem(parseNetValue(dto.netValue()))
                .fiscalQuarter(calculateHpeQuarter(entryDate))
                .fiscalYear(calculateFiscalYear(entryDate))
                .orderReason("SAP Ingestion")
                .customer(customer)
                .build();
    }

    private LocalDate parseDate(String raw, String sorg) {
        if (raw == null || raw.isBlank()) return LocalDate.now();
        String cleanRaw = raw.trim();

        boolean isNorthAmerica = (sorg != null && (sorg.startsWith("US") || sorg.startsWith("CA")));
        String[] formats = isNorthAmerica
                ? new String[]{"MM/dd/yyyy", "M/d/yyyy", "MM/dd/yy", "dd/MM/yyyy", "yyyy-MM-dd"}
                : new String[]{"dd/MM/yyyy", "d/M/yyyy", "dd/MM/yy", "MM/dd/yyyy", "yyyy-MM-dd"};

        for (String format : formats) {
            try {
                return LocalDate.parse(cleanRaw, DateTimeFormatter.ofPattern(format));
            } catch (Exception ignored) {}
        }

        try {
            return LocalDate.of(1899, 12, 30).plusDays((long) Double.parseDouble(cleanRaw));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private FiscalQuarter calculateHpeQuarter(LocalDate date) {
        int m = date.getMonthValue();
        if (m == 11 || m == 12 || m == 1) return FiscalQuarter.Q1;
        if (m >= 2 && m <= 4) return FiscalQuarter.Q2;
        if (m >= 5 && m <= 7) return FiscalQuarter.Q3;
        return FiscalQuarter.Q4;
    }

    private Integer calculateFiscalYear(LocalDate date) {
        int year = date.getYear();
        return (date.getMonthValue() >= 11) ? year + 1 : year;
    }

    private BigDecimal parseNetValue(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            // Limpia todo lo que no sea número, punto o signo menos
            String cleaned = value.replaceAll("[^\\d.-]", "");
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            log.warn("No se pudo convertir el monto: {}", value);
            return BigDecimal.ZERO;
        }
    }
}