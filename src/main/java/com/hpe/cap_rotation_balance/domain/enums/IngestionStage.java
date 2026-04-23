package com.hpe.cap_rotation_balance.domain.enums;

public enum IngestionStage {
    EMPTY,
    PARTIAL_DATA,   // Tenemos Raw Data o Precios, pero no ambos
    FINALIZED       // El cruce de archivos se completó
}