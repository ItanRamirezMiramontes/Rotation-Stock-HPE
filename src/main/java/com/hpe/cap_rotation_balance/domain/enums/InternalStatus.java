package com.hpe.cap_rotation_balance.domain.enums;

/**
 * Control interno del ciclo de vida del registro en el sistema.
 * Se eliminan bloqueos de CAP por requerimiento de mánager.
 */
public enum InternalStatus {
    LOADED,      // Datos básicos ingresados
    SYNCED,      // Precios sincronizados correctamente
    DUPLICATE,   // Registro ignorado por ya existir
    ERROR        // Error en procesamiento de la fila
}