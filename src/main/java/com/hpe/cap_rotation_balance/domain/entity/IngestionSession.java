package com.hpe.cap_rotation_balance.domain.entity;

import com.hpe.cap_rotation_balance.domain.enums.IngestionStage;
import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class IngestionSession {
    private IngestionStage stage;
    private List<ExcelOrderDTO> rawOrders;
    private Map<String, BigDecimal> priceMap; // Key: hpeOrderId, Value: Unit Price
    private LocalDateTime lastUpdate;
}
