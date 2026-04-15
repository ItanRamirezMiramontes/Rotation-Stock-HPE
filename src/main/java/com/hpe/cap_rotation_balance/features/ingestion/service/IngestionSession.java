package com.hpe.cap_rotation_balance.features.ingestion.service;

import com.hpe.cap_rotation_balance.domain.enums.IngestionStage;
import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionSession {
    private IngestionStage stage;
    private List<ExcelOrderDTO> rawOrders;

    // CAMBIO: Debe ser BigDecimal para coincidir con la lógica de negocio
    private Map<String, BigDecimal> priceMap;
    private LocalDateTime lastUpdate;

    public void clear() {
        this.stage = IngestionStage.EMPTY;
        if (this.rawOrders != null) this.rawOrders.clear();
        if (this.priceMap != null) this.priceMap.clear();
        this.rawOrders = null;
        this.priceMap = null;
    }
}