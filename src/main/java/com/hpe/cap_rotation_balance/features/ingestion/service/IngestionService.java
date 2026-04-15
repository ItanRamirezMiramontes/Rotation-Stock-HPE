package com.hpe.cap_rotation_balance.features.ingestion.service;

import com.hpe.cap_rotation_balance.domain.entity.Customer;
import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.repository.CustomerRepository;
import com.hpe.cap_rotation_balance.domain.repository.SalesOrderRepository;
import com.hpe.cap_rotation_balance.features.ingestion.component.ExcelReader;
import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import com.hpe.cap_rotation_balance.features.ingestion.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final OrderMapper orderMapper;
    private final ExcelReader excelReader;

    @Transactional
    public void processExcelIngestion(MultipartFile file) {
        List<ExcelOrderDTO> dtos = excelReader.readExcel(file);
        Map<String, Customer> customerCache = new HashMap<>();

        for (ExcelOrderDTO dto : dtos) {
            try {
                SalesOrder order = orderMapper.toEntity(dto);

                if (order.getCustomer() == null) continue;

                String customerId = order.getCustomer().getCustomerId();

                Customer finalCustomer = customerCache.computeIfAbsent(customerId, id ->
                        customerRepository.findById(id)
                                .orElseGet(() -> customerRepository.save(order.getCustomer()))
                );

                order.setCustomer(finalCustomer);
                orderRepository.save(order);

            } catch (Exception e) {
                log.error("Error en orden {}: {}", dto.orderId(), e.getMessage());
            }
        }
    }
}