package com.hpe.cap_rotation_balance.domain.enums;

public enum IngestionStage {
    EMPTY,          // No hay datos cargados
    PARTIAL_RAW,    // Se cargó el Raw Data (faltan precios)
    PARTIAL_PRICE,  // Se cargó el Price Report (pero no hay órdenes base)
    READY_TO_SAVE   // Tenemos el cruce completo de ambos archivos
}