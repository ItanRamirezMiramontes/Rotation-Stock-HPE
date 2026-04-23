package com.hpe.cap_rotation_balance.features.ingestion.api;

import com.hpe.cap_rotation_balance.domain.entity.Customer;
import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.repository.CustomerRepository;
import com.hpe.cap_rotation_balance.domain.repository.SalesOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final SalesOrderRepository salesOrderRepository;

    @GetMapping
    public ResponseEntity<List<Customer>> getAll() {
        return ResponseEntity.ok(customerRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable String id) {
        return customerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new EntityNotFoundException("Customer " + id + " not found."));
    }

    @GetMapping("/{id}/orders")
    public ResponseEntity<List<SalesOrder>> getOrdersByCustomer(@PathVariable String id) {
        if (!customerRepository.existsById(id)) {
            throw new EntityNotFoundException("Customer " + id + " not found.");
        }
        return ResponseEntity.ok(salesOrderRepository.findByCustomer_CustomerId(id));
    }
}