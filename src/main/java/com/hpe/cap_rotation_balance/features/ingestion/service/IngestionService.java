package com.hpe.cap_rotation_balance.features.ingestion.service;

import com.hpe.cap_rotation_balance.domain.entity.AuditLog;
import com.hpe.cap_rotation_balance.domain.entity.Customer;
import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.enums.IngestionStage;
import com.hpe.cap_rotation_balance.domain.enums.InternalStatus;
import com.hpe.cap_rotation_balance.domain.enums.OrderType;
import com.hpe.cap_rotation_balance.domain.repository.AuditLogRepository;
import com.hpe.cap_rotation_balance.domain.repository.CustomerRepository;
import com.hpe.cap_rotation_balance.domain.repository.SalesOrderRepository;
import com.hpe.cap_rotation_balance.features.ingestion.component.ExcelReader;
import com.hpe.cap_rotation_balance.features.ingestion.dto.CapBalanceDTO;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionService {

    private final ExcelReader excelReader;
    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final OrderMapper orderMapper;
    private final BalanceService balanceService;
    private final AuditLogRepository auditRepository;

    @Transactional
    public IngestionResponseDTO handleFileUpload(MultipartFile file) {
        log.info("[SAP-INTEGRATION] Starting ingestion: {}", file.getOriginalFilename());
        if (excelReader.isRawDataReport(file)) {
            return processRawData(file);
        } else if (excelReader.isPriceReport(file)) {
            return processPriceReport(file);
        } else {
            throw new IllegalArgumentException("Invalid file format.");
        }
    }

    private IngestionResponseDTO processRawData(MultipartFile file) {
        List<ExcelOrderDTO> dtos = excelReader.readExcel(file);
        int savedCount = 0;

        for (ExcelOrderDTO dto : dtos) {
            try {
                // CORRECCIÓN: Tu entidad usa hpeOrderId como @Id
                SalesOrder order = orderRepository.findById(dto.orderId())
                        .orElse(new SalesOrder());

                // Aseguramos que el ID se asigne si es nuevo
                if (order.getHpeOrderId() == null) order.setHpeOrderId(dto.orderId());

                Customer customer = resolveCustomer(dto);
                orderMapper.updateRawData(order, dto);
                order.setCustomer(customer);
                order.setStage(IngestionStage.PARTIAL_RAW);
                order.setUpdatedAt(OffsetDateTime.now());

                orderRepository.save(order);
                savedCount++;
            } catch (Exception e) {
                log.error("[ROW-ERROR] ID {}: {}", dto.orderId(), e.getMessage());
            }
        }
        createAuditEntry("RAW_INGESTION", savedCount, "File: " + file.getOriginalFilename());
        return new IngestionResponseDTO(IngestionStage.PARTIAL_RAW, savedCount, 0, "Loaded");
    }

    @Transactional // <--- Asegúrate que la importación sea org.springframework.transaction.annotation.Transactional
    public IngestionResponseDTO processPriceReport(MultipartFile file) {
        Map<String, BigDecimal> prices = excelReader.readPriceMap(file);
        // Usamos un array de un solo elemento o AtomicInteger porque estamos dentro de un lambda (ifPresent)
        final int[] overCapCount = {0};
        final int[] updated = {0};

        prices.forEach((orderId, amount) -> {
            orderRepository.findById(orderId).ifPresent(order -> {
                // Validación de CAP para Retornos o ZRES
                if (order.getOrderType() == OrderType.RETURN || order.getOrderType() == OrderType.ZRES) {
                    validateCapLimit(order, amount);
                    if (order.getInternalStatus() == InternalStatus.OVER_CAP_LIMIT) {
                        overCapCount[0]++;
                    }
                }

                order.setNetValueItem(amount);
                order.setUpdatedAt(OffsetDateTime.now());

                // Lógica de transición de estado
                order.setStage(order.getCustPoRef() != null ?
                        IngestionStage.READY_TO_SAVE : IngestionStage.PARTIAL_PRICE);

                orderRepository.save(order);
                updated[0]++;
            });
        });

        createAuditEntry("PRICE_INGESTION", updated[0], "Warnings: " + overCapCount[0]);
        return new IngestionResponseDTO(IngestionStage.PARTIAL_PRICE, 0, updated[0], "Price Report Synced");
    }

    private void validateCapLimit(SalesOrder order, BigDecimal amount) {
        CapBalanceDTO balance = balanceService.getCustomerBalance(
                order.getCustomer().getCustomerId(),
                order.getFiscalQuarter(),
                order.getFiscalYear()
        );

        if (amount.compareTo(balance.capRemaining()) > 0) {
            log.warn("[CAP-EXCEEDED] Order {}: {}", order.getHpeOrderId(), balance.capRemaining());
            order.setInternalStatus(InternalStatus.OVER_CAP_LIMIT);
        } else {
            order.setInternalStatus(InternalStatus.VALIDATED);
        }
    }

    private Customer resolveCustomer(ExcelOrderDTO dto) {
        String id = (dto.soldToPartyId() == null || dto.soldToPartyId().isBlank()) ? dto.custPoRef() : dto.soldToPartyId();
        return customerRepository.findById(id).orElseGet(() ->
                customerRepository.save(Customer.builder()
                        .customerId(id)
                        .customerName(dto.customerName())
                        .capPercentage(new BigDecimal("0.03"))
                        .build())
        );
    }

    private void createAuditEntry(String action, int records, String details) {
        auditRepository.save(AuditLog.builder()
                .timestamp(OffsetDateTime.now())
                .action(action)
                .recordsProcessed(records)
                .details(details)
                .build());
    }
    /**
     * Finalizes the ingestion process for all orders that are ready.
     * Moves orders from READY_TO_SAVE to a final state (e.g., VALIDATED).
     */
    @Transactional
    public void confirmAndSave() {
        log.info("[SAP-INTEGRATION] Confirming all READY_TO_SAVE orders...");

        // Buscamos todas las órdenes que están esperando confirmación
        List<SalesOrder> ordersToConfirm = orderRepository.findByStage(IngestionStage.READY_TO_SAVE);

        if (ordersToConfirm.isEmpty()) {
            throw new IllegalStateException("No orders found in READY_TO_SAVE stage to confirm.");
        }

        ordersToConfirm.forEach(order -> {
            // Si el status interno no es OVER_CAP_LIMIT, lo marcamos como listo
            if (order.getInternalStatus() != InternalStatus.OVER_CAP_LIMIT) {
                order.setInternalStatus(InternalStatus.VALIDATED);
            }

            // Cambiamos el stage a uno final o simplemente actualizamos
            // Dependiendo de tu flujo, podrías dejarlas en READY_TO_SAVE si ya es el final
            order.setUpdatedAt(OffsetDateTime.now());
            orderRepository.save(order);
        });

        log.info("[SAP-INTEGRATION] Successfully confirmed {} orders", ordersToConfirm.size());
    }
}