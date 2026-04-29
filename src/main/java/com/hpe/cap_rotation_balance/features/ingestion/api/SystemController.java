package com.hpe.cap_rotation_balance.features.ingestion.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.SpringApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @Autowired
    private ConfigurableApplicationContext context;

    @PostMapping("/shutdown")
    public ResponseEntity<?> shutdown() {
        // Preparamos un mensaje de confirmación antes de matar el proceso
        Map<String, String> response = Map.of("message", "Servidor deteniéndose... Puedes cerrar esta ventana.");

        // Creamos un hilo separado para el apagado
        // para dar tiempo a que el servidor envíe la respuesta HTTP al navegador
        Thread shutdownThread = new Thread(() -> {
            try {
                Thread.sleep(1000); // Espera 1 segundo
                SpringApplication.exit(context, () -> 0);
                System.exit(0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        shutdownThread.setDaemon(false);
        shutdownThread.start();

        return ResponseEntity.ok(response);
    }
}