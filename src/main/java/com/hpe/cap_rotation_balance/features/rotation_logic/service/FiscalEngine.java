package com.hpe.cap_rotation_balance.features.rotation_logic.service;

import com.hpe.cap_rotation_balance.domain.enums.FiscalQuarter;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

/**
 * Motor de cálculo para el calendario fiscal de HPE.
 * Q1: Nov, Dec, Jan | Q2: Feb, Mar, Apr | Q3: May, Jun, Jul | Q4: Aug, Sep, Oct
 */
@Service
public class FiscalEngine {

    /**
     * Calcula el año fiscal basado en la política de HPE.
     * Si la fecha es Nov o Dic, el año fiscal es el año siguiente.
     * Si es Enero, ya estamos en el año natural del año fiscal correspondiente.
     */
    public Integer calculateFiscalYear(LocalDate date) {
        if (date == null) {
            // Evitamos retornar 0 para no contaminar la DB (Hallazgo MEDIUM)
            throw new IllegalArgumentException("Entry date is required to calculate Fiscal Year.");
        }

        int month = date.getMonthValue();
        int year = date.getYear();

        // En HPE, el nuevo año fiscal comienza en Noviembre.
        // Nov 2024 y Dic 2024 pertenecen al FY2025. [cite: 186]
        return (month >= 11) ? year + 1 : year;
    }

    /**
     * Determina el Quarter fiscal.
     */
    public FiscalQuarter calculateQuarter(LocalDate date) {
        if (date == null) return null;

        int month = date.getMonthValue();
        return switch (month) {
            case 11, 12, 1 -> FiscalQuarter.Q1; // Nov, Dic, Ene [cite: 180]
            case 2, 3, 4   -> FiscalQuarter.Q2;
            case 5, 6, 7   -> FiscalQuarter.Q3;
            case 8, 9, 10  -> FiscalQuarter.Q4;
            default -> throw new IllegalArgumentException("Month not valid: " + month);
        };
    }
}