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
 * IngestionService — v4
 *
 * Changes from v3:
 * 1. handleFileUpload() is now split into handleRawDataUpload() and
 *    handlePriceUpload() — both public — so the Controller can call them
 *    independently or sequentially for the dual-file upload flow.
 * 2. Added databaseHasOrders() for the order-enforcement check in the Controller.
 * 3. Messages are fully in English (consistency fix).
 * 4. Added SalesOrderRepository.findDistinctHeaderStatuses() support via new
 *    query method referenced in SalesOrderRepository (also updated in this PR).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionService {

    private final ExcelReader          excelReader;
    private final SalesOrderRepository orderRepository;
    private final CustomerRepository   customerRepository;
    private final AuditLogRepository   auditRepository;
    private final OrderMapper          orderMapper;

    // ── PUBLIC ENTRY POINTS ───────────────────────────────────────────────

    /** Used by IngestionController for single-file requests (auto-detect type). */
    @Transactional
    public IngestionResponseDTO handleFileUpload(MultipartFile file) {
        if (excelReader.isRawDataReport(file)) {
            return handleRawDataUpload(file);
        } else if (excelReader.isPriceReport(file)) {
            return handlePriceUpload(file);
        }
        throw new IllegalArgumentException(
                "File does not match any known SAP report layout (Raw Data or Price Report)."
        );
    }

    /** Process a Raw Data Report (ZRES orders). Public so Controller can call it directly. */
    @Transactional
    public IngestionResponseDTO handleRawDataUpload(MultipartFile file) {
        log.info("Processing RAW DATA: {}", file.getOriginalFilename());
        return processRawData(file);
    }

    /** Process a Price Export Report. Public so Controller can call it directly. */
    @Transactional
    public IngestionResponseDTO handlePriceUpload(MultipartFile file) {
        log.info("Processing PRICE REPORT: {}", file.getOriginalFilename());
        return processPriceReport(file);
    }

    /**
     * Quick check used by the Controller to enforce upload order.
     * Returns true if at least one SalesOrder exists in the database.
     */
    public boolean databaseHasOrders() {
        return orderRepository.count() > 0;
    }

    // ── RAW DATA ─────────────────────────────────────────────────────────

    private IngestionResponseDTO processRawData(MultipartFile file) {
        List<ExcelOrderDTO> dtos = excelReader.readExcel(file);

        int inserted = 0, updated = 0, errors = 0;
        List<IngestionErrorDetail> errorDetails = new ArrayList<>();

        for (ExcelOrderDTO dto : dtos) {
            try {
                boolean    exists = orderRepository.existsById(dto.hpeOrderId());
                SalesOrder order  = orderRepository.findById(dto.hpeOrderId())
                        .orElse(new SalesOrder());

                Customer customer = resolveCustomer(dto.soldToParty());
                order.setCustomer(customer);
                orderMapper.updateRawData(order, dto);

                if (!exists) {
                    order.setInternalStatus("LOADED");
                    inserted++;
                } else {
                    // Preserve PRICE_SYNCED if the order already has a price
                    if (!"PRICE_SYNCED".equals(order.getInternalStatus())) {
                        order.setInternalStatus("LOADED");
                    }
                    updated++;
                }

                orderRepository.save(order);

            } catch (Exception e) {
                errors++;
                errorDetails.add(new IngestionErrorDetail(dto.hpeOrderId(), sanitize(e.getMessage())));
                log.error("Error processing order {}: {}", dto.hpeOrderId(), e.getMessage());
            }
        }

        int    total     = inserted + updated + errors;
        String statusStr = errors == 0 ? "SUCCESS" : (inserted + updated > 0 ? "PARTIAL" : "ERROR");
        String message   = String.format(
                "Raw Data processed: %d new, %d updated, %d error(s).",
                inserted, updated, errors
        );

        recordAudit("RAW_DATA_UPLOAD", inserted + updated,
                "File: " + file.getOriginalFilename() + " | Errors: " + errors);

        return new IngestionResponseDTO(
                statusStr, "RAW_DATA",
                total, inserted, updated, 0, errors,
                message, OffsetDateTime.now().toString(),
                errorDetails
        );
    }

    // ── PRICE REPORT ─────────────────────────────────────────────────────

    private IngestionResponseDTO processPriceReport(MultipartFile file) {
        Map<String, BigDecimal> prices = excelReader.readPriceMap(file);

        int    updatedCount = 0, skipped = 0;
        List<IngestionErrorDetail> errorDetails = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : prices.entrySet()) {
            String     hpeOrderId = entry.getKey();
            BigDecimal price      = entry.getValue();
            try {
                if (orderRepository.existsById(hpeOrderId)) {
                    SalesOrder order = orderRepository.findById(hpeOrderId).get();
                    order.setOrderValue(price);
                    order.setInternalStatus("PRICE_SYNCED");
                    orderRepository.save(order);
                    updatedCount++;
                } else {
                    skipped++;
                    log.debug("Order {} not found in DB — price skipped.", hpeOrderId);
                }
            } catch (Exception e) {
                errorDetails.add(new IngestionErrorDetail(hpeOrderId, sanitize(e.getMessage())));
                log.error("Error updating price for order {}: {}", hpeOrderId, e.getMessage());
            }
        }

        int    errors    = errorDetails.size();
        String statusStr = errors == 0 ? "SUCCESS" : (updatedCount > 0 ? "PARTIAL" : "ERROR");
        String message   = String.format(
                "Price Report processed: %d prices synced, %d orders not found, %d error(s).",
                updatedCount, skipped, errors
        );

        recordAudit("PRICE_REPORT_UPLOAD", updatedCount,
                "File: " + file.getOriginalFilename() + " | Skipped: " + skipped);

        return new IngestionResponseDTO(
                statusStr, "PRICE_REPORT",
                prices.size(), 0, updatedCount, skipped, errors,
                message, OffsetDateTime.now().toString(),
                errorDetails
        );
    }

    // ── HELPERS ───────────────────────────────────────────────────────────

    private Customer resolveCustomer(String soldToPartyId) {
        return customerRepository.findById(soldToPartyId).orElseGet(() -> {
            log.info("Creating new Customer: {}", soldToPartyId);
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

    /**
     * Strips internal exception class names from messages before sending
     * them to the frontend (security: don't leak stack info).
     */
    private String sanitize(String msg) {
        if (msg == null) return "Unexpected error";
        // Remove "com.xxx.YYYException: " prefixes
        return msg.replaceAll("^[\\w.]+Exception:\\s*", "").trim();
    }
}