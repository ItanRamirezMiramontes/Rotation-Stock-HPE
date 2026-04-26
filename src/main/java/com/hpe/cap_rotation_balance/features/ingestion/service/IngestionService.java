package com.hpe.cap_rotation_balance.features.ingestion.service;

import com.hpe.cap_rotation_balance.domain.entity.AuditLog;
import com.hpe.cap_rotation_balance.domain.entity.Customer;
import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.repository.AuditLogRepository;
import com.hpe.cap_rotation_balance.domain.repository.CustomerRepository;
import com.hpe.cap_rotation_balance.domain.repository.SalesOrderRepository;
import com.hpe.cap_rotation_balance.features.ingestion.component.ExcelReader;
import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import com.hpe.cap_rotation_balance.features.ingestion.dto.IngestionErrorDetail;
import com.hpe.cap_rotation_balance.features.ingestion.dto.IngestionResponseDTO;
import com.hpe.cap_rotation_balance.features.ingestion.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Servicio de ingesta — Monolito Desacoplado.
 *
 * Cambios respecto a la versión anterior:
 * 1. Upsert explícito: distingue entre INSERT y UPDATE para reportarlo al frontend.
 * 2. IngestionResponseDTO ampliado con contadores granulares y lista de errores.
 * 3. Sin dependencias externas. Solo repositorios locales.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionService {

    private final ExcelReader         excelReader;
    private final SalesOrderRepository orderRepository;
    private final CustomerRepository  customerRepository;
    private final AuditLogRepository  auditRepository;
    private final OrderMapper         orderMapper;

    @Transactional
    public IngestionResponseDTO handleFileUpload(MultipartFile file) {
        if (excelReader.isRawDataReport(file)) {
            return processRawData(file);
        } else if (excelReader.isPriceReport(file)) {
            return processPriceReport(file);
        } else {
            throw new IllegalArgumentException(
                    "El archivo no coincide con el layout de SAP (Raw Data o Price Report)."
            );
        }
    }

    // ── RAW DATA ────────────────────────────────────────────────────────────

    private IngestionResponseDTO processRawData(MultipartFile file) {
        List<ExcelOrderDTO> dtos = excelReader.readExcel(file);

        int inserted = 0, updated = 0, errors = 0;
        List<IngestionErrorDetail> errorDetails = new ArrayList<>();

        for (ExcelOrderDTO dto : dtos) {
            try {
                // Upsert explícito: findById determina si es INSERT o UPDATE
                boolean exists = orderRepository.existsById(dto.hpeOrderId());
                SalesOrder order = orderRepository.findById(dto.hpeOrderId())
                        .orElse(new SalesOrder());

                Customer customer = resolveCustomer(dto.soldToParty());
                order.setCustomer(customer);
                orderMapper.updateRawData(order, dto);

                // Solo sobreescribir el status si es un registro nuevo;
                // si ya tenía PRICE_SYNCED no lo regresamos a LOADED.
                if (!exists) {
                    order.setInternalStatus("LOADED");
                    inserted++;
                } else {
                    // Preservar PRICE_SYNCED si ya tenía precio
                    if (!"PRICE_SYNCED".equals(order.getInternalStatus())) {
                        order.setInternalStatus("LOADED");
                    }
                    updated++;
                }

                orderRepository.save(order);

            } catch (Exception e) {
                errors++;
                errorDetails.add(new IngestionErrorDetail(dto.hpeOrderId(), e.getMessage()));
                log.error("Error procesando orden {}: {}", dto.hpeOrderId(), e.getMessage());
            }
        }

        int total = inserted + updated + errors;
        String statusStr = errors == 0 ? "SUCCESS" : (inserted + updated > 0 ? "PARTIAL" : "ERROR");
        String message = String.format(
                "RAW DATA procesado: %d nuevas, %d actualizadas, %d errores.",
                inserted, updated, errors
        );

        recordAudit("RAW_DATA_UPLOAD", inserted + updated,
                "Archivo: " + file.getOriginalFilename() + " | Errores: " + errors);

        return new IngestionResponseDTO(
                statusStr, "RAW_DATA",
                total, inserted, updated, 0, errors,
                message, OffsetDateTime.now().toString(),
                errorDetails
        );
    }

    // ── PRICE REPORT ────────────────────────────────────────────────────────

    private IngestionResponseDTO processPriceReport(MultipartFile file) {
        Map<String, BigDecimal> prices = excelReader.readPriceMap(file);

        int updatedCount = 0, skipped = 0;
        List<IngestionErrorDetail> errorDetails = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : prices.entrySet()) {
            String hpeOrderId = entry.getKey();
            BigDecimal price  = entry.getValue();
            try {
                if (orderRepository.existsById(hpeOrderId)) {
                    SalesOrder order = orderRepository.findById(hpeOrderId).get();
                    order.setOrderValue(price);
                    order.setInternalStatus("PRICE_SYNCED");
                    orderRepository.save(order);
                    updatedCount++;
                } else {
                    // La orden no existe en nuestra DB todavía — se reporta como skipped
                    skipped++;
                    log.debug("Orden {} no encontrada en DB, precio omitido.", hpeOrderId);
                }
            } catch (Exception e) {
                errorDetails.add(new IngestionErrorDetail(hpeOrderId, e.getMessage()));
                log.error("Error actualizando precio de orden {}: {}", hpeOrderId, e.getMessage());
            }
        }

        int errors = errorDetails.size();
        String statusStr = errors == 0 ? "SUCCESS" : (updatedCount > 0 ? "PARTIAL" : "ERROR");
        String message = String.format(
                "PRICE REPORT procesado: %d precios sincronizados, %d órdenes no encontradas, %d errores.",
                updatedCount, skipped, errors
        );

        recordAudit("PRICE_REPORT_UPLOAD", updatedCount,
                "Archivo: " + file.getOriginalFilename() + " | Skipped: " + skipped);

        return new IngestionResponseDTO(
                statusStr, "PRICE_REPORT",
                prices.size(), 0, updatedCount, skipped, errors,
                message, OffsetDateTime.now().toString(),
                errorDetails
        );
    }

    // ── HELPERS ─────────────────────────────────────────────────────────────

    private Customer resolveCustomer(String soldToPartyId) {
        return customerRepository.findById(soldToPartyId).orElseGet(() -> {
            log.info("Creando nuevo Customer: {}", soldToPartyId);
            return customerRepository.save(
                    Customer.builder()
                            .customerId(soldToPartyId)
                            .customerName("HPE Customer " + soldToPartyId)
                            .build()
            );
        });
    }

    private void recordAudit(String action, int count, String details) {
        auditRepository.save(
                AuditLog.builder()
                        .timestamp(OffsetDateTime.now())
                        .action(action)
                        .recordsProcessed(count)
                        .details(details)
                        .build()
        );
    }
}