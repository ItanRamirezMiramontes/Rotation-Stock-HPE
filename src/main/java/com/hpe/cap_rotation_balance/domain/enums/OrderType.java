package com.hpe.cap_rotation_balance.domain.enums;

import lombok.Getter;

@Getter
public enum OrderType {
    ZRES("ZRES"),
    OTHER("OTHER"),
    UNKNOWN("UNKNOWN");

    private final String code;

    OrderType(String code) {
        this.code = code;
    }

    public static OrderType fromString(String text) {
        if (text == null || text.isBlank()) return UNKNOWN;
        String cleanText = text.trim().toUpperCase();

        // Prioridad absoluta a ZRES por requerimiento
        if (cleanText.contains("ZRES")) return ZRES;

        for (OrderType type : OrderType.values()) {
            if (type.code.equals(cleanText)) return type;
        }
        return OTHER;
    }
}