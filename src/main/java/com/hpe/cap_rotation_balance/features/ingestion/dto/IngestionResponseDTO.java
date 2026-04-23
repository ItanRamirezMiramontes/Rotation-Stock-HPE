package com.hpe.cap_rotation_balance.features.ingestion.dto;

public record IngestionResponseDTO(
        String status,          // "SUCCESS", "PARTIAL", "ERROR"
        int recordsProcessed,   // Cuántas filas se leyeron
        String message,         // Feedback para el usuario (ej: "150 órdenes ZRES cargadas")
        String timestamp        // Cuándo ocurrió la carga
) {}