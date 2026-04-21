package com.hpe.cap_rotation_balance.domain.repository;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.enums.FiscalQuarter;
import com.hpe.cap_rotation_balance.domain.enums.IngestionStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, String> {


    List<SalesOrder> findByStage(IngestionStage stage);

    List<SalesOrder> findByCustomer_CustomerId(String customerId);

    List<SalesOrder> findByCustomer_CustomerIdAndFiscalQuarterAndFiscalYear(
            String customerId,
            FiscalQuarter fiscalQuarter,
            Integer fiscalYear
    );

    long countByStage(IngestionStage stage);
}