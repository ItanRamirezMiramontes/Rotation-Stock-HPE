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
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionService {

    private final ExcelReader excelReader;
    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final OrderMapper orderMapper;

    /**
     * Método principal que orquesta la subida dependiendo del contenido del archivo.
     */
    @Transactional
    public IngestionResponseDTO handleFileUpload(MultipartFile file) {
        if (excelReader.isRawDataReport(file)) {
            log.info("Procesando archivo como Raw Data Report: {}", file.getOriginalFilename());
            return processRawData(file);
        } else if (excelReader.isPriceReport(file)) {
            log.info("Procesando archivo como Price Report: {}", file.getOriginalFilename());
            return processPriceReport(file);
        } else {
            throw new IllegalArgumentException("El formato del archivo no coincide con Raw Data ni Price Report.");
        }
    }

    /**
     * Procesa el reporte con todos los datos (Sorg, RTM, Fechas, etc).
     */
    private IngestionResponseDTO processRawData(MultipartFile file) {
        List<ExcelOrderDTO> dtos = excelReader.readExcel(file);
        Map<String, Customer> customerCache = new HashMap<>();
        int savedCount = 0;

        for (ExcelOrderDTO dto : dtos) {
            // 1. Manejo incremental de Clientes
            Customer customer = customerCache.computeIfAbsent(dto.custPoRef(), ref ->
                    customerRepository.findById(ref).orElseGet(() ->
                            customerRepository.save(Customer.builder()
                                    .customerId(ref)
                                    .customerName(ref)
                                    .build())
                    )
            );

            // 2. RECUPERAR O CREAR: Clave para no hacer replace
            SalesOrder order = orderRepository.findById(dto.orderId())
                    .orElse(new SalesOrder());

            // 3. MAPEO: El mapper ahora es inteligente y solo pisa lo que viene en el DTO
            orderMapper.updateRawData(order, dto);
            order.setCustomer(customer);

            // 4. DETERMINAR ESTADO: Si después de mapear el Raw Data ya tenía precio (del Price Report anterior)
            if (order.getNetValueItem() != null && order.getNetValueItem().compareTo(BigDecimal.ZERO) > 0) {
                order.setStage(IngestionStage.READY_TO_SAVE);
            } else {
                order.setStage(IngestionStage.PARTIAL_RAW);
            }

            orderRepository.save(order);
            savedCount++;
        }

        return new IngestionResponseDTO(IngestionStage.PARTIAL_RAW, savedCount, 0, null);
    }

    /**
     * Procesa el reporte que contiene solo IDs y Precios (Sales Document / Net Value).
     */
    @Transactional
    public IngestionResponseDTO processPriceReport(MultipartFile file) {
        Map<String, BigDecimal> prices = excelReader.readPriceMap(file);
        int updatedCount = 0;

        for (Map.Entry<String, BigDecimal> entry : prices.entrySet()) {
            String orderId = entry.getKey();
            BigDecimal price = entry.getValue();

            // RECUPERAR O CREAR: Si la orden ya existe (por Raw Data), la traemos con sus datos
            SalesOrder order = orderRepository.findById(orderId)
                    .orElse(new SalesOrder());

            if (order.getHpeOrderId() == null) {
                order.setHpeOrderId(orderId);
            }

            // SOLO ACTUALIZAMOS EL PRECIO: No tocamos sorg, fechas ni rtm
            order.setNetValueItem(price);

            // DETERMINAR ESTADO: Si ya tiene CustPoRef, significa que ya tiene los datos del Raw Data
            if (order.getCustPoRef() != null && !order.getCustPoRef().isBlank()) {
                order.setStage(IngestionStage.READY_TO_SAVE);
            } else {
                order.setStage(IngestionStage.PARTIAL_PRICE);
            }

            orderRepository.save(order);
            updatedCount++;
        }

        return new IngestionResponseDTO(IngestionStage.PARTIAL_PRICE, 0, updatedCount, null);
    }

    /**
     * Finaliza el proceso y realiza el commit lógico de las órdenes listas.
     */
    @Transactional
    public void confirmAndSave() {
        List<SalesOrder> readyOrders = orderRepository.findByStage(IngestionStage.READY_TO_SAVE);

        if (readyOrders.isEmpty()) {
            throw new IllegalStateException("No se encontraron órdenes completas (Datos + Precio) para confirmar.");
        }

        log.info("Iniciando confirmación final para {} órdenes.", readyOrders.size());

        // Aquí podrías mover a un estado final como COMPLETED si fuera necesario
        // readyOrders.forEach(o -> o.setStage(IngestionStage.COMPLETED));
        // orderRepository.saveAll(readyOrders);
    }
}