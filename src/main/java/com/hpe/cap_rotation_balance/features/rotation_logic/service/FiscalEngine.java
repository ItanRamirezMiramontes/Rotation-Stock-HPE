package com.hpe.cap_rotation_balance.features.rotation_logic.service;

import com.hpe.cap_rotation_balance.domain.enums.FiscalQuarter;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
public class FiscalEngine {

    public int calculateFiscalYear(LocalDate date) {
        if (date == null) return 0;
        return (date.getMonthValue() >= 11) ? date.getYear() + 1 : date.getYear();
    }

    public FiscalQuarter calculateQuarter(LocalDate date) {
        if (date == null) return null;
        int month = date.getMonthValue();
        return switch (month) {
            case 11, 12, 1 -> FiscalQuarter.Q1;
            case 2, 3, 4   -> FiscalQuarter.Q2;
            case 5, 6, 7   -> FiscalQuarter.Q3;
            case 8, 9, 10  -> FiscalQuarter.Q4;
            default -> throw new IllegalArgumentException("Mes no válido: " + month);
        };
    }
}