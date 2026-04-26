package com.hpe.cap_rotation_balance.features.ingestion.dto;

import java.util.List;

/**
 * DTO de respuesta tras procesar un archivo Excel.
 * Diferencia entre inserts, updates y errores para que el frontend
 * muestre un panel de resumen significativo al operador.
 */
public record IngestionResponseDTO(
        String  status,           // "SUCCESS" | "PARTIAL" | "ERROR"
        String  reportType,       // "RAW_DATA" | "PRICE_REPORT"
        int     totalRead,        // Filas leídas del Excel (excluye filtradas por OTYP)
        int     inserted,         // Registros nuevos guardados
        int     updated,          // Registros existentes actualizados (upsert)
        int     skipped,          // Filas ignoradas (vacías, tipo incorrecto, etc.)
        int     errors,           // Filas que lanzaron excepción
        String  message,          // Resumen legible para el usuario
        String  timestamp,
        List<IngestionErrorDetail> errorDetails  // Lista de HPE Order IDs fallidos con motivo
) {
    /** Constructor simplificado para casos de error total (archivo inválido). */
    public static IngestionResponseDTO ofError(String message, String timestamp) {
        return new IngestionResponseDTO(
                "ERROR", "UNKNOWN", 0, 0, 0, 0, 0,
                message, timestamp, List.of()
        );
    }
}