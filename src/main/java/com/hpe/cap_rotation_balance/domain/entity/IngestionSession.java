package com.hpe.cap_rotation_balance.domain.entity;

import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class IngestionSession {
    private String sessionStatus; // "PENDING_PRICES", "COMPLETED"
    private List<ExcelOrderDTO> currentBatch;
    private LocalDateTime lastActivity;
}