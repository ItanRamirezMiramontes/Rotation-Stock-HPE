package com.hpe.cap_rotation_balance.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private OffsetDateTime timestamp;

    @Column(nullable = false)
    private String action; // Ejemplo: "UPLOAD_RAW_DATA", "UPLOAD_PRICE_REPORT"

    private Integer recordsProcessed;

    @Column(length = 1000)
    private String details;
}