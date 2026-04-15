package com.hpe.cap_rotation_balance.features.rotation_logic.service;

import com.hpe.cap_rotation_balance.domain.enums.FiscalQuarter;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
public class FiscalEngine {

    /**
     * Calcula el año fiscal.
     * Si el mes es Noviembre (11) o Diciembre (12), ya pertenece al siguiente año fiscal.
     */
    public int calculateFiscalYear(LocalDate date) {
        return (date.getMonthValue() >= 11) ? date.getYear() + 1 : date.getYear();
    }

    /**
     * Determina el Quarter (Trimestre) fiscal de HPE.
     * Q1: Nov, Dec, Jan
     * Q2: Feb, Mar, Apr
     * Q3: May, Jun, Jul
     * Q4: Aug, Sep, Oct
     */
    public FiscalQuarter calculateQuarter(LocalDate date) {
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