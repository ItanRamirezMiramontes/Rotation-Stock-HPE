package com.hpe.cap_rotation_balance.domain.repository;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, String> {
}