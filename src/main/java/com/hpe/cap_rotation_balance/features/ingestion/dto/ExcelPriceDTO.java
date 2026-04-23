package com.hpe.cap_rotation_balance.features.ingestion.dto;

import java.math.BigDecimal;

public record ExcelPriceDTO(
        String hpeOrderId,
        BigDecimal price
) {}