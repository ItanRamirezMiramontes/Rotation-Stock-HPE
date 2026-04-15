package com.hpe.cap_rotation_balance.domain.enums;

public enum OrderType {
    FULLY_INVOICED("Fully Invoiced"),
    NOTHING_INVOICED("Nothing Invoiced"),
    PARTIALLY_INVOICED("Partially Invoiced");

    //VA A TRONAR
    private final String description;

    OrderType(String description) {
        this.description = description;
    }

    // Método mágico para convertir el texto del Excel al Enum correcto
    public static OrderType fromString(String text) {
        for (OrderType type : OrderType.values()) {
            if (type.description.equalsIgnoreCase(text.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("No se reconoce el tipo de orden: " + text);
    }
}