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
        if (excelReader.isRawDataReport(file)) {
            return processRawData(file);
        } else if (excelReader.isPriceReport(file)) {
            return processPriceReport(file);
        } else {
            throw new IllegalArgumentException("Formato no reconocido.");
        }
    }

    private IngestionResponseDTO processRawData(MultipartFile file) {
        List<ExcelOrderDTO> dtos = excelReader.readExcel(file);
        int saved = 0;
        for (ExcelOrderDTO dto : dtos) {
            // Usamos Sold To Party ID para el cliente, no el CustPORef
            String customerId = (dto.soldToPartyId() == null || dto.soldToPartyId().isBlank())
                    ? dto.custPoRef() : dto.soldToPartyId();

            Customer customer = customerRepository.findById(customerId)
                    .orElseGet(() -> customerRepository.save(Customer.builder()
                            .customerId(customerId)
                            .customerName(dto.customerName())
                            .build()));

            SalesOrder order = orderRepository.findById(dto.orderId()).orElse(new SalesOrder());

            // Mapeamos datos básicos (SIN PRECIO)
            orderMapper.updateRawData(order, dto);
            order.setCustomer(customer);

            // Forzamos el estado a PARTIAL_RAW porque falta el reporte de precios oficial
            order.setStage(IngestionStage.PARTIAL_RAW);

            orderRepository.save(order);
            saved++;
        }
        return new IngestionResponseDTO(IngestionStage.PARTIAL_RAW, saved, 0, null);
    }

    private IngestionResponseDTO processPriceReport(MultipartFile file) {
        Map<String, BigDecimal> prices = excelReader.readPriceMap(file);
        int updated = 0;
        for (Map.Entry<String, BigDecimal> entry : prices.entrySet()) {
            SalesOrder order = orderRepository.findById(entry.getKey()).orElse(new SalesOrder());
            if (order.getHpeOrderId() == null) order.setHpeOrderId(entry.getKey());

            order.setNetValueItem(entry.getValue());
            order.setUpdatedAt(OffsetDateTime.now());

            if (order.getCustPoRef() != null && !order.getCustPoRef().isBlank()) {
                order.setStage(IngestionStage.READY_TO_SAVE);
            } else {
                order.setStage(IngestionStage.PARTIAL_PRICE);
            }
            orderRepository.save(order);
            updated++;
        }
        return new IngestionResponseDTO(IngestionStage.PARTIAL_PRICE, 0, updated, null);
    }

    @Transactional
    public void confirmAndSave() {
        List<SalesOrder> ready = orderRepository.findByStage(IngestionStage.READY_TO_SAVE);
        if (ready.isEmpty()) throw new IllegalStateException("No hay órdenes completas.");
        ready.forEach(o -> o.setStage(IngestionStage.READY_TO_SAVE)); // Aquí podrías cambiar a COMPLETED si gustas
        orderRepository.saveAll(ready);
    }

}