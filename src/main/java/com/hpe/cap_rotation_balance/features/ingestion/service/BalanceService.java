package com.hpe.cap_rotation_balance.features.ingestion.service;

import com.hpe.cap_rotation_balance.domain.entity.Customer;
import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.enums.OrderStatus;
import com.hpe.cap_rotation_balance.domain.enums.OrderType;
import com.hpe.cap_rotation_balance.domain.repository.CustomerRepository;
import com.hpe.cap_rotation_balance.domain.repository.SalesOrderRepository;
import com.hpe.cap_rotation_balance.features.ingestion.dto.CapBalanceDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    public CapBalanceDTO getCustomerBalance(String customerId, String quarter, int year) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));

        // 1. Calcular Ventas Totales (ZRES / Facturado)
        BigDecimal totalSales = orderRepository.findByCustomer_CustomerIdAndFiscalQuarterAndFiscalYear(customerId, quarter, year)
                .stream()
                .filter(o -> o.getOrderType() == OrderType.ZRES && o.getHeaderStatus() == OrderStatus.INV)
                .map(SalesOrder::getNetValueItem)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Calcular CAP Autorizado (Ej: 3% de las ventas)
        BigDecimal percentage = customer.getCapPercentage() != null ? customer.getCapPercentage() : new BigDecimal("0.03");
        BigDecimal capAuthorized = totalSales.multiply(percentage);

        // 3. Calcular CAP Usado (Órdenes de retorno ya aplicadas)
        // Aquí filtrarías por tipos de orden de retorno (ej. RMA)
        BigDecimal capUsed = orderRepository.findByCustomer_CustomerIdAndFiscalQuarterAndFiscalYear(customerId, quarter, year)
                .stream()
                .filter(o -> o.getOrderType() == OrderType.RETURN) // Asumiendo que definiste RETURN
                .map(SalesOrder::getNetValueItem)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CapBalanceDTO(
                customerId,
                quarter,
                totalSales,
                capAuthorized,
                capUsed,
                capAuthorized.subtract(capUsed)
        );
    }
}