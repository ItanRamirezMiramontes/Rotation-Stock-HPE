package com.hpe.cap_rotation_balance.features.ingestion.api;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderRepository salesOrderRepository;

    @GetMapping
    public Page<SalesOrder> getAll(Pageable pageable) {
        return salesOrderRepository.findAll(pageable);
    }

    @GetMapping("/region/{region}")
    public ResponseEntity<List<SalesOrder>> getByRegion(@PathVariable String region) {
        return ResponseEntity.ok(salesOrderRepository.findByOmRegion(region));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<SalesOrder>> getRecent() {
        return ResponseEntity.ok(salesOrderRepository.findTop10ByOrderByUpdatedAtDesc());
    }
}