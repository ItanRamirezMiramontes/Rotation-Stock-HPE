package com.hpe.cap_rotation_balance.domain.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {
    INV("INVOICED"),
    OPN("OPEN"),
    CANC("CANCELLED"),
    UNKNOWN("UNKNOWN");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public static OrderStatus fromString(String text) {
        if (text == null || text.isBlank()) return UNKNOWN;
        String t = text.trim().toUpperCase();

        if (t.contains("INV")) return INV;
        if (t.contains("OPN") || t.contains("OPEN")) return OPN;
        if (t.contains("CANC")) return CANC;

        return UNKNOWN;
    }
}