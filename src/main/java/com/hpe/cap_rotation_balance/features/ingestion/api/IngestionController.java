package com.hpe.cap_rotation_balance.features.ingestion.api;

import com.hpe.cap_rotation_balance.features.ingestion.service.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping("/upload-report")
    public ResponseEntity<?> uploadHPEReport(@RequestParam("file") MultipartFile file) {

        // 1. Validar si el archivo está vacío
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "No se puede procesar un archivo vacío"));
        }

        // 2. Validar el formato
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("error", "Formato no soportado, debe ser .xlsx o .xls"));
        }

        try {
            // CORRECCIÓN: Usar el nombre de método exacto que definimos en IngestionService
            ingestionService.processExcelIngestion(file);

            return ResponseEntity.ok(Map.of(
                    "message", "Reporte procesado exitosamente",
                    "fileName", fileName
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Error al procesar el excel",
                            "details", e.getMessage()
                    ));
        }
    }
}