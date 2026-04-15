package com.hpe.cap_rotation_balance.features.ingestion.dto;


public record ExcelOrderDTO(
        String orderId,      // Col A (0)
        String headerStatus,       // Col B (1)
        String omRegion,     // Col C (2)
        String sorg,         // Col D (3)
        String salesOffice,  // Col E (4)
        String salesGroup,   // Col F (5)
        String otyp,         // Col G (6)
        String entryDate,    // Col H (7)
        String custPoRef,    // Col I (8)
        String soldToParty,  // Col J (9)
        String shipToAddress,// Col K (10)
        String rtm,          // Col L (11)
        String currency,     // Col M (12)
        String netValue,
        String type // Col N (13)
) {

}