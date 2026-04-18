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

@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionService {

    private final ExcelReader excelReader;
    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final OrderMapper orderMapper;

    @Transactional
    public IngestionResponseDTO handleFileUpload(MultipartFile file) {
        log.info("==> Iniciando procesamiento de archivo: {}", file.getOriginalFilename());

        if (excelReader.isRawDataReport(file)) {
            log.info("Reporte detectado: RAW DATA");
            return processRawData(file);
        } else if (excelReader.isPriceReport(file)) {
            log.info("Reporte detectado: PRICE REPORT");
            return processPriceReport(file);
        } else {
            log.error("Error: El archivo '{}' no tiene un formato de cabeceras reconocido.", file.getOriginalFilename());
            throw new IllegalArgumentException("Formato no reconocido.");
        }
    }

    private IngestionResponseDTO processRawData(MultipartFile file) {
        List<ExcelOrderDTO> dtos = excelReader.readExcel(file);
        log.info("Filas leídas del Excel Raw: {}", dtos.size());
        int saved = 0;

        for (ExcelOrderDTO dto : dtos) {
            try {
                // Identificar Cliente
                String customerId = (dto.soldToPartyId() == null || dto.soldToPartyId().isBlank())
                        ? dto.custPoRef() : dto.soldToPartyId();

                Customer customer = customerRepository.findById(customerId)
                        .orElseGet(() -> {
                            log.info("Creando nuevo cliente no registrado: ID {}", customerId);
                            return customerRepository.save(Customer.builder()
                                    .customerId(customerId)
                                    .customerName(dto.customerName())
                                    .build());
                        });

                // Buscar orden existente o crear nueva
                SalesOrder order = orderRepository.findById(dto.orderId()).orElse(new SalesOrder());
                boolean isUpdate = order.getHpeOrderId() != null;

                // Mapear datos y persistir
                orderMapper.updateRawData(order, dto);
                order.setCustomer(customer);
                order.setStage(IngestionStage.PARTIAL_RAW);
                order.setUpdatedAt(OffsetDateTime.now());

                orderRepository.save(order);
                saved++;

                if (saved % 100 == 0) log.debug("Progreso Raw Data: {} registros procesados", saved);

            } catch (Exception e) {
                log.error("Error procesando fila de Raw Data (ID: {}): {}", dto.orderId(), e.getMessage());
            }
        }

        log.info("Finalizado proceso RAW. Total guardados/actualizados: {}", saved);
        return new IngestionResponseDTO(IngestionStage.PARTIAL_RAW, saved, 0, null);
    }

    @Transactional
    public IngestionResponseDTO processPriceReport(MultipartFile file) {
        Map<String, BigDecimal> prices = excelReader.readPriceMap(file);
        log.info("Entradas encontradas en el mapa de precios del Excel: {}", prices.size());

        int updated = 0;
        int notFound = 0;

        for (Map.Entry<String, BigDecimal> entry : prices.entrySet()) {
            String orderId = entry.getKey();
            BigDecimal newPrice = entry.getValue();

            // Log de diagnóstico para ver exactamente qué ID se busca
            log.debug("Intentando aplicar precio. Buscando ID en BD: [{}] con precio: {}", orderId, newPrice);

            Optional<SalesOrder> existingOrderOpt = orderRepository.findById(orderId);

            if (existingOrderOpt.isPresent()) {
                SalesOrder order = existingOrderOpt.get();

                log.info("MATCH ENCONTRADO para Orden {}. Actualizando Precio: {} -> {}",
                        orderId, order.getNetValueItem(), newPrice);

                order.setNetValueItem(newPrice);
                order.setUpdatedAt(OffsetDateTime.now());

                // Lógica de transición de estados
                if (order.getCustPoRef() != null && !order.getCustPoRef().isBlank()) {
                    order.setStage(IngestionStage.READY_TO_SAVE);
                    log.debug("Orden {} movida a READY_TO_SAVE", orderId);
                } else {
                    order.setStage(IngestionStage.PARTIAL_PRICE);
                    log.warn("Orden {} actualizada con precio pero falta CustPoRef. Estado: PARTIAL_PRICE", orderId);
                }

                orderRepository.save(order);
                updated++;
            } else {
                // Este log es la clave: si aparece mucho, los IDs entre archivos no coinciden (formato)
                log.warn("ORDEN NO ENCONTRADA: El ID [{}] del Price Report no existe en la base de datos.", orderId);
                notFound++;
            }
        }

        // Forzar guardado en BD antes de retornar
        orderRepository.flush();

        log.info("Finalizado proceso PRICE. Actualizados: {}, No encontrados en BD: {}", updated, notFound);
        return new IngestionResponseDTO(IngestionStage.PARTIAL_PRICE, 0, updated, null);
    }

    @Transactional
    public void confirmAndSave() {
        log.info("Iniciando confirmación final de ingesta...");
        List<SalesOrder> ready = orderRepository.findByStage(IngestionStage.READY_TO_SAVE);

        if (ready.isEmpty()) {
            log.warn("Intento de confirmación fallido: No hay órdenes en estado READY_TO_SAVE.");
            throw new IllegalStateException("No hay órdenes completas para confirmar.");
        }

        int count = ready.size();
        // Aquí puedes cambiar el stage a COMPLETED o mantenerlo según tu lógica
        ready.forEach(o -> {
            o.setStage(IngestionStage.READY_TO_SAVE);
            o.setUpdatedAt(OffsetDateTime.now());
        });

        orderRepository.saveAll(ready);
        log.info("Confirmación exitosa. {} órdenes procesadas.", count);
    }
}