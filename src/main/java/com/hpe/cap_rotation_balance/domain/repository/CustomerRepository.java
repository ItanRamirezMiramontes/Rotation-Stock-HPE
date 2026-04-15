package com.hpe.cap_rotation_balance.domain.repository;

import com.hpe.cap_rotation_balance.domain.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {
}