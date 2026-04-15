package com.hpe.cap_rotation_balance.features.ingestion.api;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderRepository salesOrderRepository;

    @GetMapping
    public ResponseEntity<Page<SalesOrder>> getOrders(@RequestParam(defaultValue = "0") int page) {
        Pageable pageable = PageRequest.of(page, 10, Sort.by("hpeOrderId").ascending());
        return ResponseEntity.ok(salesOrderRepository.findAll(pageable));
    }
}
