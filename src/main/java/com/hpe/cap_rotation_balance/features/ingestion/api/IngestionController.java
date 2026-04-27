package com.hpe.cap_rotation_balance.features.ingestion.api;

import com.hpe.cap_rotation_balance.domain.entity.AuditLog;
import com.hpe.cap_rotation_balance.domain.repository.AuditLogRepository;
import com.hpe.cap_rotation_balance.features.ingestion.dto.IngestionResponseDTO;
import com.hpe.cap_rotation_balance.features.ingestion.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * IngestionController — v4 (fixes applied)
 *
 * FIX 1 — DUAL UPLOAD:
 *   POST /ingestion/upload now accepts rawFile + priceFile in a single multipart
 *   request. The frontend sends both at once; each is optional but at least one
 *   must be non-empty.
 *
 * FIX 2 — ORDER ENFORCEMENT:
 *   If priceFile is sent WITHOUT rawFile AND there are no existing orders in DB
 *   for the orders referenced in priceFile, the endpoint returns HTTP 409 with a
 *   clear message asking the user to upload the Raw Data Report first.
 *   This prevents a Price Report being orphaned with no matching base records.
 *
 * FIX 3 — LAST UPLOAD TIMESTAMP:
 *   GET /ingestion/last  returns the most recent AuditLog entry so the frontend
 *   can show "Last updated: MMM dd yyyy HH:mm" next to the Upload button.
 */
@Slf4j
@RestController
@RequestMapping("/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService   ingestionService;
    private final AuditLogRepository auditLogRepository;

    // ── SINGLE OR DUAL UPLOAD ──────────────────────────────────────────────

    /**
     * Accepts one or two files in a single request:
     *   rawFile   — SAP Raw Data Report  (.xlsx)
     *   priceFile — SAP Price Export     (.xlsx)
     *
     * Rules:
     *   - At least one file must be present and non-empty.
     *   - If only priceFile is sent, the service validates that the DB already
     *     contains at least one order matching a referenced HPE Order ID; otherwise
     *     it returns HTTP 409 so the user knows to load Raw Data first.
     *   - If both are sent, Raw Data is always processed before Price Report.
     *
     * The frontend's single drop-zone selects files via the HTML <input multiple>
     * attribute. The JS layer detects each file's type by header inspection and
     * assigns it to the correct part name before submission.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(
            @RequestParam(value = "rawFile",   required = false) MultipartFile rawFile,
            @RequestParam(value = "priceFile", required = false) MultipartFile priceFile
    ) {
        boolean hasRaw   = rawFile   != null && !rawFile.isEmpty();
        boolean hasPrice = priceFile != null && !priceFile.isEmpty();

        if (!hasRaw && !hasPrice) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No files received. Please attach at least one SAP Excel report."));
        }

        // Validate file extensions before doing any work
        if (hasRaw   && !isExcel(rawFile.getOriginalFilename())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Raw Data file must be .xlsx or .xls"));
        }
        if (hasPrice && !isExcel(priceFile.getOriginalFilename())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Price Report file must be .xlsx or .xls"));
        }

        log.info("Ingestion request — rawFile: {}, priceFile: {}",
                hasRaw   ? rawFile.getOriginalFilename()   : "none",
                hasPrice ? priceFile.getOriginalFilename() : "none");

        // FIX 2: Enforce order — Price without Raw only allowed if DB has prior records
        if (hasPrice && !hasRaw) {
            boolean dbHasOrders = ingestionService.databaseHasOrders();
            if (!dbHasOrders) {
                return ResponseEntity.status(409).body(Map.of(
                        "error",
                        "No orders found in the database. " +
                                "Please upload the Raw Data Report first before uploading a Price Report."
                ));
            }
        }

        // Process in correct order: Raw first, then Price
        if (hasRaw && hasPrice) {
            IngestionResponseDTO rawResult   = ingestionService.handleRawDataUpload(rawFile);
            IngestionResponseDTO priceResult = ingestionService.handlePriceUpload(priceFile);
            // Return a combined response
            return ResponseEntity.ok(Map.of(
                    "rawData",     rawResult,
                    "priceReport", priceResult
            ));
        }

        if (hasRaw) {
            return ResponseEntity.ok(ingestionService.handleRawDataUpload(rawFile));
        }

        // hasPrice only (DB already validated above)
        return ResponseEntity.ok(ingestionService.handlePriceUpload(priceFile));
    }

    // ── LAST UPLOAD TIMESTAMP ─────────────────────────────────────────────

    /**
     * Returns the most recent AuditLog entry.
     * Used by the frontend to display "Last updated: MMM dd, yyyy HH:mm" next
     * to the Upload / Download buttons in the Orders page header.
     */
    @GetMapping("/last")
    public ResponseEntity<?> getLastUpload() {
        List<AuditLog> logs = auditLogRepository.findAllByOrderByTimestampDesc();
        if (logs.isEmpty()) {
            return ResponseEntity.ok(Map.of("hasData", false));
        }
        AuditLog last = logs.get(0);
        return ResponseEntity.ok(Map.of(
                "hasData",   true,
                "action",    last.getAction(),
                "timestamp", last.getTimestamp().toString(),
                "records",   last.getRecordsProcessed() != null ? last.getRecordsProcessed() : 0
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok("Ingestion API is online. Ready for SAP Reports.");
    }

    // ── HELPERS ───────────────────────────────────────────────────────────

    private boolean isExcel(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }
}