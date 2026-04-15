package com.hpe.cap_rotation_balance.features.ingestion.component;

import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Component
public class ExcelReader {

    private final DataFormatter formatter = new DataFormatter();

    public List<ExcelOrderDTO> readExcel(MultipartFile file) {
        List<ExcelOrderDTO> dtos = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return dtos;

            Map<String, Integer> colMap = mapHeaders(headerRow);

            // LOG DE DIAGNÓSTICO: Si ves "false" en la consola, el Excel no tiene esa columna exacta
            log.info("Columnas detectadas: Sales Office: {}, Sales Group: {}",
                    colMap.containsKey("Sales Office"), colMap.containsKey("Sales Group"));

            Integer priceColIdx = colMap.get("Order value incl tax");
            if (priceColIdx == null) priceColIdx = colMap.get("Net price in USD");

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row, colMap.get("HPE Order"))) continue;

                // Construcción del DTO - ORDEN ESTRICTO (13 campos)
                dtos.add(new ExcelOrderDTO(
                        getVal(row, colMap.get("HPE Order")),
                        getVal(row, colMap.get("Int Header Status")),
                        getVal(row, colMap.get("OM Region")),
                        getVal(row, colMap.get("Sorg")),
                        getVal(row, colMap.get("Sales Office")), // Columna 5
                        getVal(row, colMap.get("Sales Group")),  // Columna 6
                        getVal(row, colMap.get("OTYP")),
                        getVal(row, colMap.get("Order Entry Date")),
                        getVal(row, colMap.get("CustPORef")),
                        getVal(row, colMap.get("Ship-to address")),
                        getVal(row, colMap.get("RTM")),
                        getVal(row, colMap.get("Local currency")),
                        getVal(row, priceColIdx)
                ));
            }
        } catch (Exception e) {
            log.error("Error crítico leyendo Excel: {}", e.getMessage());
        }
        return dtos;
    }

    private Map<String, Integer> mapHeaders(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : headerRow) {
            String headerName = formatter.formatCellValue(cell).trim();
            if (!headerName.isEmpty()) {
                map.put(headerName, cell.getColumnIndex());
            }
        }
        return map;
    }

    private String getVal(Row row, Integer index) {
        if (index == null || index == -1) return "";
        Cell cell = row.getCell(index);
        return (cell == null) ? "" : formatter.formatCellValue(cell).trim();
    }

    private boolean isRowEmpty(Row row, Integer criticalIndex) {
        if (row == null || criticalIndex == null) return true;
        return getVal(row, criticalIndex).isEmpty();
    }

    public boolean isRawDataReport(MultipartFile file) { return checkHeaderPresence(file, "HPE Order"); }
    public boolean isPriceReport(MultipartFile file) { return checkHeaderPresence(file, "Sales Document"); }

    private boolean checkHeaderPresence(MultipartFile file, String header) {
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Row row = workbook.getSheetAt(0).getRow(0);
            for (Cell c : row) {
                if (formatter.formatCellValue(c).trim().equalsIgnoreCase(header)) return true;
            }
        } catch (Exception e) { return false; }
        return false;
    }

    public Map<String, BigDecimal> readPriceMap(MultipartFile file) {
        Map<String, BigDecimal> prices = new HashMap<>();
        // ... (Tu implementación de readPriceMap se mantiene igual)
        return prices;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(value.replaceAll("[^\\d.-]", "")); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }
}