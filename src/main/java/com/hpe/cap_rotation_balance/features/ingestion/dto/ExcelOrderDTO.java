package com.hpe.cap_rotation_balance.features.ingestion.dto;

import lombok.Builder;
import java.time.LocalDate;

@Builder
public record ExcelOrderDTO(
        String hpeOrderId,
        String headerStatus,
        String invoiceHeaderStatus,
        String omRegion,
        String sorg,
        String salesOffice,
        String salesGroup,
        String orderType, // ZRES
        LocalDate entryDate,
        String custPoRef,
        String soldToParty,
        String shipToAddress,
        String rtm,
        String currency
) {}