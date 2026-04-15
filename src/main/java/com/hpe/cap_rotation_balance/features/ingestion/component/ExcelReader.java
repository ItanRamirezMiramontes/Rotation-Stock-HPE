package com.hpe.cap_rotation_balance.features.ingestion.component;

import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Component
public class ExcelReader {

    private final DataFormatter formatter = new DataFormatter();

    public List<ExcelOrderDTO> readExcel(MultipartFile file) {
        List<ExcelOrderDTO> dtos = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            if (rows.hasNext()) {
                rows.next();
            }

            while (rows.hasNext()) {
                Row currentRow = rows.next();

                if (isRowEmpty(currentRow)) {
                    continue;
                }

                try {
                    ExcelOrderDTO dto = new ExcelOrderDTO(
                            getCellValue(currentRow, 0),  // 1. HPE Order ID
                            getCellValue(currentRow, 1),  // 2. Int Header Status
                            getCellValue(currentRow, 2),  // 3. OM Region
                            getCellValue(currentRow, 3),  // 4. Sorg
                            getCellValue(currentRow, 4),  // 5. Sales Office
                            getCellValue(currentRow, 5),  // 6. Sales Group
                            getCellValue(currentRow, 6),  // 7. OTYP (Type)
                            getCellValue(currentRow, 7),  // 8. Order Entry Date
                            getCellValue(currentRow, 8),  // 9. CustPORef
                            getCellValue(currentRow, 9),  // 10. Sold To Party ID
                            getCellValue(currentRow, 10), // 11. Ship-to address
                            getCellValue(currentRow, 11), // 12. RTM
                            getCellValue(currentRow, 12), // 13. Local currency
                            getCellValue(currentRow, 13), // 14. Net Value / Order Value
                            getCellValue(currentRow, 14)  // 15. CAMPO FALTANTE (Ajusta el índice si es necesario)
                    );

                    dtos.add(dto);
                } catch (Exception e) {
                    log.warn("Error parseando la fila número {}: {}", currentRow.getRowNum(), e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("Error crítico de E/S al leer el archivo Excel: {}", e.getMessage());
            throw new RuntimeException("No se pudo procesar el archivo Excel correctamente");
        }

        return dtos;
    }

    /**
     * Obtiene el valor de una celda y lo formatea como String, sin importar su tipo original en Excel.
     */
    private String getCellValue(Row row, int cellIndex) {
        Cell cell = row.getCell(cellIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        // El formatter maneja automáticamente fechas, números con decimales y textos.
        return formatter.formatCellValue(cell).trim();
    }

    /**
     * Verifica si una fila está vacía comprobando si la primera celda (HPE Order) no tiene datos.
     */
    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        Cell firstCell = row.getCell(0);
        return firstCell == null || firstCell.getCellType() == CellType.BLANK ||
                formatter.formatCellValue(firstCell).trim().isEmpty();
    }
}