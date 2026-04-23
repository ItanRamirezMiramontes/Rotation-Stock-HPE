package com.hpe.cap_rotation_balance.features.ingestion.component;

import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import com.hpe.cap_rotation_balance.common.util.DateParserUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Component
public class ExcelReader {
    private final DataFormatter formatter = new DataFormatter();

    public List<ExcelOrderDTO> readExcel(MultipartFile file) {
        log.info("Iniciando procesamiento de RAW DATA SAP: {}", file.getOriginalFilename());
        List<ExcelOrderDTO> dtos = new ArrayList<>();

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> colMap = mapHeaders(sheet.getRow(0));

            // Validación de cabeceras críticas para el reporte final
            validateHeaders(colMap, List.of("HPE Order", "OTYP", "OM Region", "Sold To Party ID"));

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row, colMap.get("HPE Order"))) continue;

                String orderType = getVal(row, colMap.get("OTYP"));

                // REQUERIMIENTO MÁNGER: Filtro OBLIGATORIO ZRES
                if (!"ZRES".equalsIgnoreCase(orderType)) {
                    continue;
                }

                // Construcción del DTO usando el Builder de Lombok
                ExcelOrderDTO dto = ExcelOrderDTO.builder()
                        .hpeOrderId(readSapId(row, colMap.get("HPE Order")))
                        .headerStatus(getVal(row, colMap.get("Int Header Status")))
                        .invoiceHeaderStatus(getVal(row, colMap.get("Invoice Header Status")))
                        .omRegion(getVal(row, colMap.get("OM Region")))
                        .sorg(getVal(row, colMap.get("Sorg")))
                        .salesOffice(getVal(row, colMap.get("Sales Office")))
                        .salesGroup(getVal(row, colMap.get("Sales Group")))
                        .orderType(orderType)
                        .entryDate(parseDate(row, colMap.get("Order Entry Date")))
                        .custPoRef(getVal(row, colMap.get("CustPORef")))
                        .soldToParty(readSapId(row, colMap.get("Sold To Party ID")))
                        .shipToAddress(getVal(row, colMap.get("Ship-to address")))
                        .rtm(getVal(row, colMap.get("RTM")))
                        .currency(getVal(row, colMap.get("Local currency")))
                        .build();

                dtos.add(dto);
            }
            log.info("Carga finalizada: {} órdenes procesadas.", dtos.size());
        } catch (Exception e) {
            log.error("Error crítico en lectura: {}", e.getMessage());
            throw new IllegalArgumentException("Error al procesar el archivo Excel: " + e.getMessage());
        }
        return dtos;
    }

    public Map<String, BigDecimal> readPriceMap(MultipartFile file) {
        Map<String, BigDecimal> prices = new HashMap<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> colMap = mapHeaders(sheet.getRow(0));

            // Soporte para distintos nombres de columna en reportes de precio de SAP
            Integer idIdx = colMap.get("Sales Document");
            if (idIdx == null) idIdx = colMap.get("HPE Order");

            Integer priceIdx = colMap.get("Net Value (Item)");
            if (priceIdx == null) priceIdx = colMap.get("Net Value");

            if (idIdx == null || priceIdx == null) {
                throw new IllegalArgumentException("No se encontraron las columnas de ID o Precio.");
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String id = readSapId(row, idIdx);
                if (!id.isEmpty()) {
                    prices.put(id, parseBigDecimal(getVal(row, priceIdx)));
                }
            }
        } catch (Exception e) {
            log.error("Error en Price Map: {}", e.getMessage());
        }
        return prices;
    }

    // Métodos utilitarios corregidos para evitar errores de tipo
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

    private void validateHeaders(Map<String, Integer> colMap, List<String> required) {
        for (String h : required) {
            if (!colMap.containsKey(h)) throw new IllegalArgumentException("Falta columna: " + h);
        }
    }

    private String getVal(Row row, Integer index) {
        if (index == null) return "";
        Cell cell = row.getCell(index);
        return (cell == null) ? "" : formatter.formatCellValue(cell).trim();
    }

    private LocalDate parseDate(Row row, Integer index) {
        if (index == null) return null;
        Cell cell = row.getCell(index);
        if (cell == null) return null;
        try {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            return DateParserUtil.parse(formatter.formatCellValue(cell));
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.replaceAll("[^\\d.-]", ""));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private boolean isRowEmpty(Row row, Integer criticalIndex) {
        if (row == null || criticalIndex == null) return true;
        return readSapId(row, criticalIndex).isEmpty();
    }

    // Métodos para el IngestionService
    public boolean isRawDataReport(MultipartFile file) {
        return checkHeader(file, "HPE Order") && checkHeader(file, "OTYP");
    }

    public boolean isPriceReport(MultipartFile file) {
        return checkHeader(file, "Sales Document") || (checkHeader(file, "HPE Order") && checkHeader(file, "Net Value"));
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
}