package com.hpe.cap_rotation_balance.features.ingestion.api;

import com.hpe.cap_rotation_balance.domain.entity.Customer;
import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.repository.CustomerRepository;
import com.hpe.cap_rotation_balance.domain.repository.SalesOrderRepository;
import com.hpe.cap_rotation_balance.features.ingestion.dto.CapBalanceDTO;
import com.hpe.cap_rotation_balance.features.ingestion.service.BalanceService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for managing Customer-related data and their associated Sales Orders.
 */
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final BalanceService balanceService;

    /**
     * Retrieves all customers loaded in the system.
     * Throws 404 if no customers are found.
     */
    @GetMapping
    public ResponseEntity<List<Customer>> getAll() {
        List<Customer> customers = customerRepository.findAll();
        if (customers.isEmpty()) {
            throw new EntityNotFoundException("There are no clients loaded into the system.");
        }
        return ResponseEntity.ok(customers);
    }

    /**
     * Retrieves a specific customer by its ID.
     * Throws 404 if the customer does not exist.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable String id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Customer with ID " + id + " was not found."));
        return ResponseEntity.ok(customer);
    }

    /**
     * Retrieves all sales orders associated with a specific customer.
     * Path: /customers/{id}/orders
     * Throws 404 if the customer doesn't exist or has no orders registered.
     */
    @GetMapping("/{id}/orders")
    public ResponseEntity<List<SalesOrder>> getOrdersByCustomer(@PathVariable String id) {
        // 1. Validate customer existence
        if (!customerRepository.existsById(id)) {
            throw new EntityNotFoundException("Customer with ID " + id + " was not found in the system.");
        }

        // 2. Retrieve orders using the repository method
        List<SalesOrder> orders = salesOrderRepository.findByCustomer_CustomerId(id);

        // 3. Throw exception if the list is empty to trigger 404 response
        if (orders.isEmpty()) {
            throw new EntityNotFoundException("There are no registered orders for customer: " + id);
        }

        return ResponseEntity.ok(orders);
    }
    @GetMapping("/{id}/balance")
    public ResponseEntity<CapBalanceDTO> getBalance(
            @PathVariable String id,
            @RequestParam String quarter,
            @RequestParam int year) {
        return ResponseEntity.ok(balanceService.getCustomerBalance(id, quarter, year));
    }
}