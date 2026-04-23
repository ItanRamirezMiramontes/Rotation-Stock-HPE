package com.hpe.cap_rotation_balance.features.ingestion.api;

import com.hpe.cap_rotation_balance.features.ingestion.dto.IngestionResponseDTO;
import com.hpe.cap_rotation_balance.features.ingestion.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    /**
     * Carga de archivos SAP.
     * Procesa y guarda inmediatamente en DB sin confirmación manual (Requerimiento simplificado).
     */
    @PostMapping("/upload")
    public ResponseEntity<IngestionResponseDTO> uploadFile(@RequestParam("file") MultipartFile file) {
        log.info("Iniciando carga de archivo: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            throw new IllegalArgumentException("El archivo seleccionado está vacío.");
        }

        IngestionResponseDTO response = ingestionService.handleFileUpload(file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok("Ingestion API is online. Ready for SAP Reports.");
    }
}