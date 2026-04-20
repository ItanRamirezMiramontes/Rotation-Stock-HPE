package com.hpe.cap_rotation_balance.features.ingestion.api;

import com.hpe.cap_rotation_balance.features.ingestion.dto.IngestionResponseDTO;
import com.hpe.cap_rotation_balance.features.ingestion.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    /**
     * Upload SAP files (Raw Data or Price Report).
     * The system automatically detects the report type by its headers.
     */
    @PostMapping("/upload")
    public ResponseEntity<IngestionResponseDTO> uploadFile(@RequestParam("file") MultipartFile file) {
        log.info("Receiving file for ingestion...");

        // Si por alguna razón Spring deja pasar un archivo nulo o vacío
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File content is empty. Please upload a non-empty Excel/CSV file.");
        }

        IngestionResponseDTO response = ingestionService.handleFileUpload(file);
        return ResponseEntity.ok(response);
    }

    /**
     * Confirms orders in READY_TO_SAVE state.
     * Completes the state machine cycle and persists data.
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirmIngestion() {
        log.info("Confirming ingestion session...");

        // The service should throw IllegalStateException if no data is ready to be saved
        ingestionService.confirmAndSave();

        return ResponseEntity.ok(Map.of(
                "message", "Ingestion successfully confirmed and persisted",
                "status", "COMPLETED"
        ));
    }

    /**
     * Health check and status of the current ingestion session.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "service", "Ingestion API",
                "active", true,
                "message", "System is ready for new SAP data imports"
        ));
    }
}