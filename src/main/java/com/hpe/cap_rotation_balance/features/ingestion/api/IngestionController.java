package com.hpe.cap_rotation_balance.features.ingestion.api;

import com.hpe.cap_rotation_balance.features.ingestion.dto.IngestionResponseDTO;
import com.hpe.cap_rotation_balance.features.ingestion.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
     * Endpoint para cargar archivos de SAP (Raw Data o Price Report).
     * El sistema detecta automáticamente el tipo de reporte por los encabezados.
     */
    @PostMapping("/upload")
    public ResponseEntity<IngestionResponseDTO> uploadFile(@RequestParam("file") MultipartFile file) {
        log.info("Recibiendo archivo para ingesta: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            IngestionResponseDTO response = ingestionService.handleFileUpload(file);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Archivo no reconocido: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        } catch (Exception e) {
            log.error("Error crítico en la carga: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint para confirmar las órdenes que están en estado READY_TO_SAVE.
     * Esto finaliza el ciclo del autómata de estados.
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, String>> confirmIngestion() {
        try {
            ingestionService.confirmAndSave();
            return ResponseEntity.ok(Map.of(
                    "message", "Ingesta confirmada exitosamente",
                    "status", "COMPLETED"
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno al confirmar la ingesta"));
        }
    }

    /**
     * Opcional: Endpoint de salud de la ingesta para Postman.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        // Aquí podrías inyectar un método del service que cuente órdenes por stage
        return ResponseEntity.ok(Map.of("service", "Ingestion API", "active", true));
    }
}