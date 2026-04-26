package com.hpe.cap_rotation_balance.features.ingestion.dto;

/**
 * Detalle de una fila que falló durante la ingesta.
 * Se incluye en IngestionResponseDTO.errorDetails para que el frontend
 * pueda mostrar una lista colapsable de órdenes problemáticas.
 */
public record IngestionErrorDetail(
        String hpeOrderId,   // ID de la orden (o "fila N" si el ID estaba vacío)
        String reason        // Mensaje de error corto
) {}