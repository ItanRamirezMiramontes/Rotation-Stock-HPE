package com.hpe.cap_rotation_balance.features.ingestion.dto;

import com.hpe.cap_rotation_balance.domain.enums.IngestionStage;

public record IngestionResponseDTO(
        IngestionStage stage,           // EMPTY, PARTIAL, READY
        int totalRecords,               // Cuántas órdenes hay en total
        int recordsWithPrice,           // Cuántas ya tienen precio
        String failedOrders// Una pequeña muestra para que el usuario valide
) {}