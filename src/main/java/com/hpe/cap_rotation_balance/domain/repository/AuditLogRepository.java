package com.hpe.cap_rotation_balance.domain.repository;

import com.hpe.cap_rotation_balance.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    // Ver el historial de carga cronológicamente
    List<AuditLog> findAllByOrderByTimestampDesc();
}