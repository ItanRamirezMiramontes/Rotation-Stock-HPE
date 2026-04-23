package com.hpe.cap_rotation_balance.domain.repository;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, String> {

    // Para ver todas las órdenes de un Sold To Party (Cliente)
    List<SalesOrder> findByCustomer_CustomerId(String customerId);

    // Para filtrar por Región (Requerimiento Prioritario del Mánager)
    List<SalesOrder> findByOmRegion(String region);

    // Auditoría: Obtener las últimas órdenes cargadas/actualizadas
    List<SalesOrder> findTop10ByOrderByUpdatedAtDesc();
}