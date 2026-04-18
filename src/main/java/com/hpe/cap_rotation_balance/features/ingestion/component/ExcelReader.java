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
        log.info("Iniciando lectura de RAW DATA Excel...");
        List<ExcelOrderDTO> dtos = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> colMap = mapHeaders(sheet.getRow(0));

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row, colMap.get("HPE Order"))) continue;

                dtos.add(new ExcelOrderDTO(
                        readSapId(row, colMap.get("HPE Order")),
                        getVal(row, colMap.get("Int Header Status")),
                        getVal(row, colMap.get("OM Region")),
                        getVal(row, colMap.get("Sorg")),
                        getVal(row, colMap.get("Sales Office")),
                        getVal(row, colMap.get("Sales Group")),
                        getVal(row, colMap.get("OTYP")),
                        getVal(row, colMap.get("Order Entry Date")),
                        getVal(row, colMap.get("CustPORef")),
                        getVal(row, colMap.get("Ship-to address")),
                        getVal(row, colMap.get("RTM")),
                        getVal(row, colMap.get("Local currency")),
                        getVal(row, colMap.get("Sold To Party ID")),
                        getVal(row, colMap.get("Ship-to Name")),
                        getVal(row, colMap.get("Order Reason Code"))
                ));
            }
        } catch (Exception e) {
            log.error("Error en Raw Data: {}", e.getMessage());
        }
        return dtos;
    }

    public Map<String, BigDecimal> readPriceMap(MultipartFile file) {
        log.info("Iniciando mapeo de precios (Price Report)...");
        Map<String, BigDecimal> prices = new HashMap<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> colMap = mapHeaders(sheet.getRow(0));

            // CORRECCIÓN BASADA EN TUS LOGS:
            // Intentar encontrar el ID de la orden
            Integer idIdx = colMap.get("Sales Document");
            if (idIdx == null) idIdx = colMap.get("HPE Order");

            // Intentar encontrar el Precio (en tus logs aparece como "Net Value (Item)")
            Integer priceIdx = colMap.get("Net Value (Item)");
            if (priceIdx == null) priceIdx = colMap.get("Net Value (Header)");
            if (priceIdx == null) priceIdx = colMap.get("Net Value");

            log.info("Índices detectados - ID: {}, Precio: {}", idIdx, priceIdx);

            if (idIdx == null || priceIdx == null) {
                log.error("No se pudieron encontrar las columnas necesarias. Cabeceras disponibles: {}", colMap.keySet());
                return prices;
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String id = readSapId(row, idIdx);
                BigDecimal price = parseBigDecimal(getVal(row, priceIdx));

                if (id != null && !id.isEmpty()) {
                    prices.put(id, price);
                }
            }
            log.info("Mapa de precios creado. Registros: {}", prices.size());
        } catch (Exception e) {
            log.error("Error en Price Report: {}", e.getMessage());
        }
        return prices;
    }

    private String readSapId(Row row, Integer index) {
        if (index == null) return "";
        Cell cell = row.getCell(index);
        if (cell == null) return "";

        if (cell.getCellType() == CellType.NUMERIC) {
            return String.format("%.0f", cell.getNumericCellValue());
        }
        return formatter.formatCellValue(cell).trim();
    }

    private Map<String, Integer> mapHeaders(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow == null) return map;
        for (Cell cell : headerRow) {
            String name = formatter.formatCellValue(cell).trim();
            if (!name.isEmpty()) map.put(name, cell.getColumnIndex());
        }
        return map;
    }

    private String getVal(Row row, Integer index) {
        if (index == null) return "";
        Cell cell = row.getCell(index);
        return (cell == null) ? "" : formatter.formatCellValue(cell).trim();
    }

    private boolean isRowEmpty(Row row, Integer criticalIndex) {
        if (row == null || criticalIndex == null) return true;
        return readSapId(row, criticalIndex).isEmpty();
    }

    public boolean isRawDataReport(MultipartFile file) {
        return checkHeader(file, "HPE Order");
    }

    public boolean isPriceReport(MultipartFile file) {
        // En tus logs, el price report tiene "Sales Document"
        return checkHeader(file, "Sales Document") || checkHeader(file, "Net Value (Item)");
    }

    private boolean checkHeader(MultipartFile file, String target) {
        try (InputStream is = file.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Row r = wb.getSheetAt(0).getRow(0);
            for (Cell c : r) {
                if (formatter.formatCellValue(c).trim().equalsIgnoreCase(target)) return true;
            }
        } catch (Exception e) { return false; }
        return false;
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.replaceAll("[^\\d.-]", ""));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}