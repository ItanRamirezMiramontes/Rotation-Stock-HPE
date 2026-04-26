package com.hpe.cap_rotation_balance.features.ingestion.api;

import com.hpe.cap_rotation_balance.domain.entity.SalesOrder;
import com.hpe.cap_rotation_balance.domain.repository.SalesOrderRepository;
import com.hpe.cap_rotation_balance.domain.specification.SalesOrderSpec;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controlador de Órdenes — Monolito Desacoplado.
 *
 * Todos los endpoints consultan exclusivamente la DB local.
 * No existe dependencia de servicios SAP externos.
 *
 * Endpoints:
 *   GET /orders               — Lista paginada con filtros dinámicos
 *   GET /orders/filters       — Valores disponibles para poblar dropdowns en el frontend
 *   GET /orders/stats         — Contadores para las stat-cards del dashboard
 *   GET /orders/export        — Descarga Excel con los filtros activos
 *   GET /orders/recent        — Últimas 10 órdenes modificadas (widget de auditoría)
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderRepository orderRepository;

    // ── LISTA PAGINADA CON FILTROS ──────────────────────────────────────────

    /**
     * @param region     filtro opcional por OM Region (case-insensitive)
     * @param quarter    filtro opcional por Fiscal Quarter (Q1, Q2, Q3, Q4)
     * @param year       filtro opcional por Fiscal Year
     * @param customerId filtro opcional por Sold To Party ID
     * @param status     filtro opcional por internalStatus (LOADED, PRICE_SYNCED)
     * @param pageable   paginación y ordenamiento estándar de Spring (?page=0&size=20&sort=updatedAt,desc)
     */
    @GetMapping
    public Page<SalesOrder> getAll(
            @RequestParam(required = false) String  region,
            @RequestParam(required = false) String  quarter,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String  customerId,
            @RequestParam(required = false) String  status,
            Pageable pageable
    ) {
        Specification<SalesOrder> spec = Specification.where(null);
        spec = spec.and(SalesOrderSpec.byRegion(region));
        spec = spec.and(SalesOrderSpec.byFiscalQuarter(quarter));
        spec = spec.and(SalesOrderSpec.byFiscalYear(year));
        spec = spec.and(SalesOrderSpec.byCustomerId(customerId));
        spec = spec.and(SalesOrderSpec.byInternalStatus(status));
        return orderRepository.findAll(spec, pageable);
    }

    // ── VALORES DISPONIBLES PARA DROPDOWNS ─────────────────────────────────

    /**
     * Devuelve los valores únicos presentes en la DB para poblar los
     * filtros del frontend sin hardcodear nada en el HTML.
     */
    @GetMapping("/filters")
    public ResponseEntity<Map<String, Object>> getAvailableFilters() {
        return ResponseEntity.ok(Map.of(
                "regions",  orderRepository.findDistinctRegions(),
                "quarters", orderRepository.findDistinctFiscalQuarters(),
                "years",    orderRepository.findDistinctFiscalYears()
        ));
    }

    // ── STAT CARDS DEL DASHBOARD ────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long total        = orderRepository.count();
        long loaded       = orderRepository.countByInternalStatus("LOADED");
        long priceSynced  = orderRepository.countByInternalStatus("PRICE_SYNCED");
        // pricePending = órdenes sin precio (internalStatus=LOADED y orderValue null)
        long pricePending = orderRepository.count(SalesOrderSpec.pricePending());

        return ResponseEntity.ok(Map.of(
                "total",        total,
                "loaded",       loaded,
                "priceSynced",  priceSynced,
                "pricePending", pricePending
        ));
    }

    // ── EXPORTACIÓN A EXCEL ─────────────────────────────────────────────────

    /**
     * Genera y devuelve un archivo .xlsx con las órdenes que coincidan
     * con los filtros activos. El frontend hace window.location.href a esta URL.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String  region,
            @RequestParam(required = false) String  quarter,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String  customerId
    ) throws Exception {

        Specification<SalesOrder> spec = Specification.where(null);
        spec = spec.and(SalesOrderSpec.byRegion(region));
        spec = spec.and(SalesOrderSpec.byFiscalQuarter(quarter));
        spec = spec.and(SalesOrderSpec.byFiscalYear(year));
        spec = spec.and(SalesOrderSpec.byCustomerId(customerId));

        List<SalesOrder> orders = orderRepository.findAll(spec);

        byte[] excelBytes = buildExcel(orders);

        String filename = buildFilename(region, quarter, year);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    // ── AUDITORÍA ───────────────────────────────────────────────────────────

    @GetMapping("/recent")
    public ResponseEntity<List<SalesOrder>> getRecent() {
        return ResponseEntity.ok(orderRepository.findTop10ByOrderByUpdatedAtDesc());
    }

    // ── HELPERS PRIVADOS ────────────────────────────────────────────────────

    private byte[] buildExcel(List<SalesOrder> orders) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Final Report");

            // Cabeceras (alineadas a las 15 columnas del reporte final)
            String[] headers = {
                    "HPE Order ID", "Header Status", "Invoice Status",
                    "OM Region", "Sorg", "Sales Office", "Sales Group",
                    "Order Type", "Entry Date", "Cust PO Ref",
                    "Sold To Party", "Ship-To Address", "RTM",
                    "Currency", "Order Value", "Fiscal Quarter", "Fiscal Year",
                    "Internal Status", "Updated At"
            };

            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Datos
            int rowIdx = 1;
            for (SalesOrder o : orders) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(safe(o.getHpeOrderId()));
                row.createCell(1).setCellValue(safe(o.getHeaderStatus()));
                row.createCell(2).setCellValue(safe(o.getInvoiceHeaderStatus()));
                row.createCell(3).setCellValue(safe(o.getOmRegion()));
                row.createCell(4).setCellValue(safe(o.getSorg()));
                row.createCell(5).setCellValue(safe(o.getSalesOffice()));
                row.createCell(6).setCellValue(safe(o.getSalesGroup()));
                row.createCell(7).setCellValue(safe(o.getOrderType()));
                row.createCell(8).setCellValue(safeDate(o.getEntryDate()));
                row.createCell(9).setCellValue(safe(o.getCustPoRef()));
                row.createCell(10).setCellValue(o.getCustomer() != null ? safe(o.getCustomer().getCustomerId()) : "");
                row.createCell(11).setCellValue(safe(o.getShipToAddress()));
                row.createCell(12).setCellValue(safe(o.getRtm()));
                row.createCell(13).setCellValue(safe(o.getCurrency()));
                if (o.getOrderValue() != null) {
                    row.createCell(14).setCellValue(o.getOrderValue().doubleValue());
                } else {
                    row.createCell(14).setCellValue("PENDIENTE");
                }
                row.createCell(15).setCellValue(safe(o.getFiscalQuarter()));
                row.createCell(16).setCellValue(o.getFiscalYear() != null ? o.getFiscalYear() : 0);
                row.createCell(17).setCellValue(safe(o.getInternalStatus()));
                row.createCell(18).setCellValue(o.getUpdatedAt() != null ? o.getUpdatedAt().toString() : "");
            }

            // Auto-resize primeras columnas
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private String buildFilename(String region, String quarter, Integer year) {
        StringBuilder sb = new StringBuilder("HPE_FinalReport");
        if (region  != null) sb.append("_").append(region.toUpperCase());
        if (quarter != null) sb.append("_").append(quarter.toUpperCase());
        if (year    != null) sb.append("_FY").append(year);
        sb.append(".xlsx");
        return sb.toString();
    }

    private String safe(String v)      { return v != null ? v : ""; }
    private String safeDate(LocalDate d) { return d != null ? d.toString() : ""; }
}