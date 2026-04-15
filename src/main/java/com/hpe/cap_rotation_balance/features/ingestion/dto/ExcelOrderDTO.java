package com.hpe.cap_rotation_balance.features.ingestion.dto;

public record ExcelOrderDTO(
        String orderId,
        String headerStatus,
        String omRegion,
        String sorg,
        String salesOffice,
        String salesGroup,
        String type,
        String entryDate,
        String custPoRef,
        String shipToAddress,
        String rtm,
        String currency,
        String netValue
) {}