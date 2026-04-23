package com.hpe.cap_rotation_balance.features.ingestion.service;

import com.hpe.cap_rotation_balance.domain.entity.AuditLog;
import com.hpe.cap_rotation_balance.domain.entity.Customer;
import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.repository.AuditLogRepository;
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
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionService {

    private final ExcelReader excelReader;
    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogRepository auditRepository;
    private final OrderMapper orderMapper;

    /**
     * Orquestador de carga. Detecta el tipo de reporte y delega la persistencia.
     */
    @Transactional
    public IngestionResponseDTO handleFileUpload(MultipartFile file) {
        if (excelReader.isRawDataReport(file)) {
            return processRawData(file);
        } else if (excelReader.isPriceReport(file)) {
            return processPriceReport(file);
        } else {
            throw new IllegalArgumentException("El archivo no coincide con el layout de SAP (Raw Data o Price Report).");
        }
    }

    /**
     * Procesa el reporte de órdenes (ZRES).
     */
    private IngestionResponseDTO processRawData(MultipartFile file) {
        List<ExcelOrderDTO> dtos = excelReader.readExcel(file);
        int savedCount = 0;

        for (ExcelOrderDTO dto : dtos) {
            try {
                // Buscar orden existente o crear nueva (Evita duplicados por ID de HPE)
                SalesOrder order = orderRepository.findById(dto.hpeOrderId())
                        .orElse(new SalesOrder());

                // Vincular con el Cliente (Sold To Party)
                Customer customer = resolveCustomer(dto.soldToParty());
                order.setCustomer(customer);

                // Usamos el Mapper auditado para settear los campos y el periodo fiscal informativo
                orderMapper.updateRawData(order, dto);

                order.setInternalStatus("LOADED");
                orderRepository.save(order);
                savedCount++;
            } catch (Exception e) {
                log.error("Fallo al procesar fila de orden {}: {}", dto.hpeOrderId(), e.getMessage());
            }
        }

        recordAudit("RAW_DATA_UPLOAD", savedCount, "Archivo: " + file.getOriginalFilename());

        return new IngestionResponseDTO(
                "SUCCESS",
                savedCount,
                "Carga exitosa de " + savedCount + " registros ZRES.",
                OffsetDateTime.now().toString()
        );
    }

    /**
     * Procesa el reporte de precios y hace el match con las órdenes cargadas.
     */
    private IngestionResponseDTO processPriceReport(MultipartFile file) {
        Map<String, BigDecimal> prices = excelReader.readPriceMap(file);
        int[] updated = {0};

        prices.forEach((hpeOrderId, price) -> {
            orderRepository.findById(hpeOrderId).ifPresent(order -> {
                order.setOrderValue(price);
                order.setInternalStatus("PRICE_SYNCED");
                orderRepository.save(order);
                updated[0]++;
            });
        });

        recordAudit("PRICE_REPORT_UPLOAD", updated[0], "Sincronizado desde: " + file.getOriginalFilename());

        return new IngestionResponseDTO(
                "SUCCESS",
                updated[0],
                "Precios actualizados para " + updated[0] + " órdenes.",
                OffsetDateTime.now().toString()
        );
    }

    private Customer resolveCustomer(String soldToPartyId) {
        return customerRepository.findById(soldToPartyId).orElseGet(() -> {
            log.info("Creando nuevo registro para Sold To Party: {}", soldToPartyId);
            return customerRepository.save(Customer.builder()
                    .customerId(soldToPartyId)
                    .customerName("HPE Customer " + soldToPartyId)
                    .build());
        });
    }

    private void recordAudit(String action, int count, String details) {
        auditRepository.save(AuditLog.builder()
                .timestamp(OffsetDateTime.now())
                .action(action)
                .recordsProcessed(count)
                .details(details)
                .build());
    }
}