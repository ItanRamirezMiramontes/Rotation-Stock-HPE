package com.hpe.cap_rotation_balance.features.ingestion.mapper;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import com.hpe.cap_rotation_balance.features.rotation_logic.service.FiscalEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Mapper simplificado para el cumplimiento de la nueva directiva:
 * Ingesta directa de datos sin bloqueos de CAP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final FiscalEngine fiscalEngine;

    /**
     * Mapea el DTO del Excel a la Entidad de Base de Datos.
     * Se enfoca en la integridad de los datos para el Final Report.
     */
    public void updateRawData(SalesOrder order, ExcelOrderDTO dto) {
        // 1. Identificadores Únicos
        order.setHpeOrderId(dto.hpeOrderId());
        order.setCustPoRef(dto.custPoRef());

        // 2. Fechas y Periodos Fiscales (Solo informativos)
        LocalDate entryDate = dto.entryDate();
        if (entryDate != null) {
            order.setEntryDate(entryDate);
            try {
                // Convertimos a String/Integer para asegurar compatibilidad con la Entity simplificada
                order.setFiscalQuarter(String.valueOf(fiscalEngine.calculateQuarter(entryDate)));
                order.setFiscalYear(fiscalEngine.calculateFiscalYear(entryDate));
            } catch (Exception e) {
                log.warn("No se pudo calcular el periodo fiscal para la orden {}: {}", dto.hpeOrderId(), e.getMessage());
            }
        }

        // 3. Atributos de Organización y Región (Prioridad Mánager)
        order.setOmRegion(dto.omRegion());
        order.setSorg(dto.sorg());
        order.setSalesOffice(dto.salesOffice());
        order.setSalesGroup(dto.salesGroup());
        order.setOrderType(dto.orderType()); // Viene como String "ZRES" del DTO

        // 4. Logística y Status SAP
        order.setRtm(dto.rtm());
        order.setShipToAddress(dto.shipToAddress());
        order.setHeaderStatus(dto.headerStatus());
        order.setInvoiceHeaderStatus(dto.invoiceHeaderStatus());

        // 5. Moneda (Tratada como String para evitar errores de Enum con SAP)
        order.setCurrency(dto.currency() != null ? dto.currency().toUpperCase() : null);
    }
}