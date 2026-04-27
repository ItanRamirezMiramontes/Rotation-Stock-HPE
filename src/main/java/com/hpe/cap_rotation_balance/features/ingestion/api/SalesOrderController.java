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
 * SalesOrderController — v4
 *
 * Changes from v3:
 * 1. Added @RequestParam "headerStatus" filter — feeds the new Header Status
 *    dropdown in the Orders filter bar.
 * 2. /orders/filters now includes "headerStatuses" list so the dropdown is
 *    populated dynamically from DB values (no hardcoding).
 * 3. /orders/export passes headerStatus through so exported file respects
 *    all active filters.
 * 4. Excel export value "PENDIENTE" corrected to English "PENDING".
 * 5. All log/comment strings migrated to English.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderRepository orderRepository;

    // ── PAGINATED LIST WITH FILTERS ───────────────────────────────────────

    @GetMapping
    public Page<SalesOrder> getAll(
            @RequestParam(required = false) String  region,
            @RequestParam(required = false) String  quarter,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String  customerId,
            @RequestParam(required = false) String  status,
            @RequestParam(required = false) String  headerStatus,   // NEW FILTER
            Pageable pageable
    ) {
        Specification<SalesOrder> spec = Specification.where(null);
        spec = spec.and(SalesOrderSpec.byRegion(region));
        spec = spec.and(SalesOrderSpec.byFiscalQuarter(quarter));
        spec = spec.and(SalesOrderSpec.byFiscalYear(year));
        spec = spec.and(SalesOrderSpec.byCustomerId(customerId));
        spec = spec.and(SalesOrderSpec.byInternalStatus(status));
        spec = spec.and(SalesOrderSpec.byHeaderStatus(headerStatus)); // NEW
        return orderRepository.findAll(spec, pageable);
    }

    // ── DROPDOWN VALUES ───────────────────────────────────────────────────

    /**
     * Returns distinct filter values from the DB.
     * Now includes headerStatuses for the new Header Status dropdown.
     */
    @GetMapping("/filters")
    public ResponseEntity<Map<String, Object>> getAvailableFilters() {
        return ResponseEntity.ok(Map.of(
                "regions",       orderRepository.findDistinctRegions(),
                "quarters",      orderRepository.findDistinctFiscalQuarters(),
                "years",         orderRepository.findDistinctFiscalYears(),
                "headerStatuses",orderRepository.findDistinctHeaderStatuses()  // NEW
        ));
    }

    // ── STAT CARDS ────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long total        = orderRepository.count();
        long loaded       = orderRepository.countByInternalStatus("LOADED");
        long priceSynced  = orderRepository.countByInternalStatus("PRICE_SYNCED");
        long pricePending = orderRepository.count(SalesOrderSpec.pricePending());

        return ResponseEntity.ok(Map.of(
                "total",        total,
                "loaded",       loaded,
                "priceSynced",  priceSynced,
                "pricePending", pricePending
        ));
    }

    // ── EXCEL EXPORT ──────────────────────────────────────────────────────

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String  region,
            @RequestParam(required = false) String  quarter,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String  customerId,
            @RequestParam(required = false) String  headerStatus
    ) throws Exception {

        Specification<SalesOrder> spec = Specification.where(null);
        spec = spec.and(SalesOrderSpec.byRegion(region));
        spec = spec.and(SalesOrderSpec.byFiscalQuarter(quarter));
        spec = spec.and(SalesOrderSpec.byFiscalYear(year));
        spec = spec.and(SalesOrderSpec.byCustomerId(customerId));
        spec = spec.and(SalesOrderSpec.byHeaderStatus(headerStatus));

        List<SalesOrder> orders = orderRepository.findAll(spec);
        byte[]           bytes  = buildExcel(orders);
        String           fname  = buildFilename(region, quarter, year);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fname + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    // ── RECENT ────────────────────────────────────────────────────────────

    @GetMapping("/recent")
    public ResponseEntity<List<SalesOrder>> getRecent() {
        return ResponseEntity.ok(orderRepository.findTop10ByOrderByUpdatedAtDesc());
    }

    // ── EXCEL BUILD HELPERS ───────────────────────────────────────────────

    private byte[] buildExcel(List<SalesOrder> orders) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Final Report");

            String[] headers = {
                    "HPE Order ID", "Header Status", "Invoice Status",
                    "OM Region", "Sorg", "Sales Office", "Sales Group",
                    "Order Type", "Entry Date", "Cust PO Ref",
                    "Sold To Party", "Ship-To Address", "RTM",
                    "Currency", "Order Value", "Fiscal Quarter", "Fiscal Year",
                    "Updated At"
            };

            CellStyle hStyle = wb.createCellStyle();
            Font      hFont  = wb.createFont();
            hFont.setBold(true);
            hStyle.setFont(hFont);
            hStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            hStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row hRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hStyle);
            }

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
                    row.createCell(14).setCellValue("PENDING");   // was "PENDIENTE"
                }
                row.createCell(15).setCellValue(safe(o.getFiscalQuarter()));
                row.createCell(16).setCellValue(o.getFiscalYear() != null ? o.getFiscalYear() : 0);
                row.createCell(17).setCellValue(o.getUpdatedAt() != null ? o.getUpdatedAt().toString() : "");
            }

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

    private String safe(String v)       { return v != null ? v : ""; }
    private String safeDate(LocalDate d){ return d != null ? d.toString() : ""; }
}