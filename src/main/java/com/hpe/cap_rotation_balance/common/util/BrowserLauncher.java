package com.hpe.cap_rotation_balance.common.util;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.awt.Desktop;
import java.net.URI;

@Component
public class BrowserLauncher {

    @EventListener(ApplicationReadyEvent.class)
    public void launchBrowser() {
        // Obligatorio para entornos Windows para que no dé error de "Headless"
        System.setProperty("java.awt.headless", "false");

        try {
            // Verificamos si el sistema operativo permite abrir navegadores
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                // Esperamos 2.5 segundos para dar tiempo a que Spring Boot inicie el servidor
                Thread.sleep(2500);
                Desktop.getDesktop().browse(new URI("http://localhost:8082"));
                System.out.println(">>> Navegador abierto automáticamente en http://localhost:8082");
            }
        } catch (Exception e) {
            System.err.println("No se pudo abrir el navegador automáticamente: " + e.getMessage());
            System.err.println("Por favor, abre manualmente: http://localhost:8082");
        }
    }
}