package com.hpe.cap_rotation_balance.features.ingestion.dto;

import java.math.BigDecimal;

public record CapBalanceDTO(
        String customerId,
        String fiscalQuarter,
        BigDecimal totalSales,
        BigDecimal capAuthorized,
        BigDecimal capUsed,
        BigDecimal capRemaining
) {}