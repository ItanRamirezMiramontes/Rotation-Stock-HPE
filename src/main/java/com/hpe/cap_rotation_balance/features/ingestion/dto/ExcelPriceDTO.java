package com.hpe.cap_rotation_balance.features.ingestion.dto;

public record ExcelPriceDTO(
        String orderId,      // El ID para hacer el match (HPE Order ID)
        String unitPrice,    // El precio que vamos a inyectar
        String netValueItem  // Valor neto si el reporte lo trae por separado
) {}