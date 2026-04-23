package com.hpe.cap_rotation_balance.common.util;

import lombok.extern.slf4j.Slf4j;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class DateParserUtil {

    private static final List<DateTimeFormatter> FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("M/d/yyyy"), // Caso SAP sin ceros
            DateTimeFormatter.ofPattern("d/M/yyyy")
    );

    public static LocalDate parse(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;

        String cleanedDate = dateStr.trim();

        // Manejo de fechas numéricas de Excel (Serial Dates)
        if (cleanedDate.matches("\\d+")) {
            try {
                return LocalDate.of(1899, 12, 30).plusDays(Long.parseLong(cleanedDate));
            } catch (Exception e) {
                log.warn("Error convirtiendo serial date de Excel: {}", cleanedDate);
            }
        }

        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDate.parse(cleanedDate, formatter);
            } catch (DateTimeParseException ignored) {}
        }

        log.error("Formato de fecha no soportado: {}", dateStr);
        return null;
    }
}