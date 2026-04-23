package com.hpe.cap_rotation_balance.features.ingestion.dto;

public record CustomerDTO(
        String customerId,
        String customerName,
        int activeOrders
) {}