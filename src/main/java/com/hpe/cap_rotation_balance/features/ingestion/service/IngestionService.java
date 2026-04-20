package com.hpe.cap_rotation_balance.features.ingestion.service;

import com.hpe.cap_rotation_balance.domain.entity.Customer;
import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.enums.IngestionStage;
import com.hpe.cap_rotation_balance.domain.repository.CustomerRepository;
import com.hpe.cap_rotation_balance.domain.repository.SalesOrderRepository;
import com.hpe.cap_rotation_balance.features.ingestion.component.ExcelReader;
import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import com.hpe.cap_rotation_balance.features.ingestion.dto.IngestionResponseDTO;
import com.hpe.cap_rotation_balance.features.ingestion.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service responsible for managing the data ingestion workflow.
 * Handles parsing, state transitions, and persistence of SAP reports.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionService {

    private final ExcelReader excelReader;
    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final OrderMapper orderMapper;

    /**
     * Main entry point for file uploads. Detects the report type and routes to the specific processor.
     * @param file The uploaded MultipartFile (Excel).
     * @return IngestionResponseDTO containing the processing summary.
     * @throws IllegalArgumentException if the file format is unrecognized.
     */
    @Transactional
    public IngestionResponseDTO handleFileUpload(MultipartFile file) {
        log.info("==> Starting file processing: {}", file.getOriginalFilename());

        if (excelReader.isRawDataReport(file)) {
            log.info("Report detected: RAW DATA");
            return processRawData(file);
        } else if (excelReader.isPriceReport(file)) {
            log.info("Report detected: PRICE REPORT");
            return processPriceReport(file);
        } else {
            log.error("Error: The file '{}' does not have a recognized header format.", file.getOriginalFilename());
            throw new IllegalArgumentException("Unrecognized file format. Please upload a valid SAP Raw Data or Price Report.");
        }
    }

    /**
     * Processes "Raw Data" reports to extract order headers and customer information.
     */
    private IngestionResponseDTO processRawData(MultipartFile file) {
        List<ExcelOrderDTO> dtos = excelReader.readExcel(file);
        log.info("Rows read from Raw Excel: {}", dtos.size());
        int saved = 0;

        for (ExcelOrderDTO dto : dtos) {
            try {
                // Identify or create Customer
                String customerId = (dto.soldToPartyId() == null || dto.soldToPartyId().isBlank())
                        ? dto.custPoRef() : dto.soldToPartyId();

                Customer customer = customerRepository.findById(customerId)
                        .orElseGet(() -> {
                            log.info("Creating new unregistered customer: ID {}", customerId);
                            return customerRepository.save(Customer.builder()
                                    .customerId(customerId)
                                    .customerName(dto.customerName())
                                    .build());
                        });

                // Find existing order or create new one
                SalesOrder order = orderRepository.findById(dto.orderId()).orElse(new SalesOrder());

                // Map data and persist
                orderMapper.updateRawData(order, dto);
                order.setCustomer(customer);
                order.setStage(IngestionStage.PARTIAL_RAW);
                order.setUpdatedAt(OffsetDateTime.now());

                orderRepository.save(order);
                saved++;

                if (saved % 100 == 0) log.debug("Raw Data progress: {} records processed", saved);

            } catch (Exception e) {
                log.error("Error processing Raw Data row (ID: {}): {}", dto.orderId(), e.getMessage());
            }
        }

        log.info("Finished RAW process. Total saved/updated: {}", saved);
        return new IngestionResponseDTO(IngestionStage.PARTIAL_RAW, saved, 0, null);
    }

    /**
     * Processes "Price Reports" to update the net values of existing orders.
     */
    @Transactional
    public IngestionResponseDTO processPriceReport(MultipartFile file) {
        Map<String, BigDecimal> prices = excelReader.readPriceMap(file);
        log.info("Entries found in Price Report map: {}", prices.size());

        int updated = 0;
        int notFound = 0;

        for (Map.Entry<String, BigDecimal> entry : prices.entrySet()) {
            String orderId = entry.getKey();
            BigDecimal newPrice = entry.getValue();

            Optional<SalesOrder> existingOrderOpt = orderRepository.findById(orderId);

            if (existingOrderOpt.isPresent()) {
                SalesOrder order = existingOrderOpt.get();

                log.info("MATCH FOUND for Order {}. Updating Price: {} -> {}", orderId, order.getNetValueItem(), newPrice);

                order.setNetValueItem(newPrice);
                order.setUpdatedAt(OffsetDateTime.now());

                // State transition logic
                if (order.getCustPoRef() != null && !order.getCustPoRef().isBlank()) {
                    order.setStage(IngestionStage.READY_TO_SAVE);
                    log.debug("Order {} moved to READY_TO_SAVE", orderId);
                } else {
                    order.setStage(IngestionStage.PARTIAL_PRICE);
                    log.warn("Order {} updated with price but missing CustPoRef. State: PARTIAL_PRICE", orderId);
                }

                orderRepository.save(order);
                updated++;
            } else {
                log.warn("ORDER NOT FOUND: The ID [{}] from Price Report does not exist in the database.", orderId);
                notFound++;
            }
        }

        orderRepository.flush();
        log.info("Finished PRICE process. Updated: {}, Not found in DB: {}", updated, notFound);
        return new IngestionResponseDTO(IngestionStage.PARTIAL_PRICE, 0, updated, null);
    }

    /**
     * Confirms all orders in READY_TO_SAVE state.
     * Completes the ingestion cycle and marks data as final.
     * @throws IllegalStateException if no orders are ready to be confirmed.
     */
    @Transactional
    public void confirmAndSave() {
        log.info("Starting final ingestion confirmation...");
        List<SalesOrder> ready = orderRepository.findByStage(IngestionStage.READY_TO_SAVE);

        if (ready.isEmpty()) {
            log.warn("Confirmation failed: No orders found with status READY_TO_SAVE.");
            throw new IllegalStateException("No complete orders available to confirm. Please upload Raw Data and Price Reports first.");
        }

        int count = ready.size();
        ready.forEach(o -> {
            // Here you could change stage to COMPLETED if a new enum exists
            o.setStage(IngestionStage.READY_TO_SAVE);
            o.setUpdatedAt(OffsetDateTime.now());
        });

        orderRepository.saveAll(ready);
        log.info("Confirmation successful. {} orders processed.", count);
    }
}