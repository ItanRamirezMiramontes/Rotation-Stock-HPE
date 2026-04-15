package com.hpe.cap_rotation_balance.features.ingestion.dto;

import com.hpe.cap_rotation_balance.domain.enums.IngestionStage;
import java.util.List;

public record IngestionResponseDTO(
        IngestionStage stage,           // EMPTY, PARTIAL, READY
        int totalRecords,               // Cuántas órdenes hay en total
        int recordsWithPrice,           // Cuántas ya tienen precio
        List<ExcelOrderDTO> previewData // Una pequeña muestra para que el usuario valide
) {}