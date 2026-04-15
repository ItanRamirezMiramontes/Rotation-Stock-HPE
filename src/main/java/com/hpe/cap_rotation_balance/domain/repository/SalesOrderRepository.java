package com.hpe.cap_rotation_balance.domain.repository;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.enums.IngestionStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, String> {

    /**
     * Busca todas las órdenes que se encuentran en un estado específico.
     * Útil para el proceso de confirmación de ingesta.
     */
    List<SalesOrder> findByStage(IngestionStage stage);

    // Opcional: Si quieres contar cuántas órdenes hay en cierto estado
    long countByStage(IngestionStage stage);
}