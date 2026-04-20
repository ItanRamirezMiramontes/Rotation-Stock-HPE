package com.hpe.cap_rotation_balance.features.ingestion.service;

import com.hpe.cap_rotation_balance.domain.entity.Customer;
import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.enums.FiscalQuarter;
import com.hpe.cap_rotation_balance.domain.enums.OrderStatus;
import com.hpe.cap_rotation_balance.domain.enums.OrderType;
import com.hpe.cap_rotation_balance.domain.repository.CustomerRepository;
import com.hpe.cap_rotation_balance.domain.repository.SalesOrderRepository;
import com.hpe.cap_rotation_balance.features.ingestion.dto.CapBalanceDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    public CapBalanceDTO getCustomerBalance(String customerId, FiscalQuarter quarter, int year) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + customerId));

        // Obtenemos todas las órdenes del periodo
        List<SalesOrder> orders = orderRepository.findByCustomer_CustomerIdAndFiscalQuarterAndFiscalYear(customerId, quarter, year);

        // 1. Calcular Ventas Totales (Tipo ZRES y Status INV)
        BigDecimal totalSales = orders.stream()
                .filter(o -> o.getOrderType() == OrderType.ZRES && o.getHeaderStatus() == OrderStatus.INV)
                .map(SalesOrder::getNetValueItem)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Calcular CAP Autorizado (Usa el % del cliente o 3% por defecto)
        BigDecimal percentage = customer.getCapPercentage() != null ? customer.getCapPercentage() : new BigDecimal("0.03");
        BigDecimal capAuthorized = totalSales.multiply(percentage);

        // 3. Calcular CAP Usado (Tipo RETURN)
        BigDecimal capUsed = orders.stream()
                .filter(o -> o.getOrderType() == OrderType.RETURN)
                .map(SalesOrder::getNetValueItem)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Calcular CAP Restante
        BigDecimal capRemaining = capAuthorized.subtract(capUsed);

        return new CapBalanceDTO(
                customerId,
                quarter.name(),
                totalSales,
                capAuthorized,
                capUsed,
                capRemaining
        );
    }
}