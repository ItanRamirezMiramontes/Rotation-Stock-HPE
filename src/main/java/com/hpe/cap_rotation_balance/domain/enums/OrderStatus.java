package com.hpe.cap_rotation_balance.domain.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {
    INV(""),
    OPEN("OPEN"),
    CANC("CANC"),
    COMP("COMP"), // Por si llega Completed abreviado
    UNKNOWN("UNKNOWN");

    private final String code;

    OrderStatus(String code) {
        this.code = code;
    }

    // Método robusto para el Mapper
    public static OrderStatus fromString(String text) {
        if (text == null || text.isBlank()) return UNKNOWN;
        String cleanText = text.trim().toUpperCase();

        for (OrderStatus status : OrderStatus.values()) {
            if (status.name().equals(cleanText) || status.code.equals(cleanText)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}