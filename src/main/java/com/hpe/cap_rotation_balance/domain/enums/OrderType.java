package com.hpe.cap_rotation_balance.domain.enums;

import lombok.Getter;

@Getter
public enum OrderType {
    ZRES("ZRES", "Returns"),
    ZDR("ZDR", "Debit Memo"),
    ZCR("ZCR", "Credit Memo"),
    OR("OR", "Standard Order"),
    UNKNOWN("UNKNOWN", "Unknown Type");

    private final String code;
    private final String description;

    OrderType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static OrderType fromString(String text) {
        if (text == null || text.isBlank()) {
            return UNKNOWN;
        }

        String cleanText = text.trim().toUpperCase();

        for (OrderType type : OrderType.values()) {
            // Comparamos contra el código (ZRES)
            if (type.code.equals(cleanText)) {
                return type;
            }
        }

        // En lugar de lanzar excepción y que muera el proceso,
        // devolvemos UNKNOWN para que la carga continúe.
        return UNKNOWN;
    }
}