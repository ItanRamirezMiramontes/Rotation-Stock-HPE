package com.hpe.cap_rotation_balance;

import com.hpe.cap_rotation_balance.features.entry_data.excel.ApachePoiReader;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

@SpringBootApplication
public class CapRotationBalanceApplication implements CommandLineRunner {

    // Inyectamos el lector (asegúrate de poner @Component en tu clase ApachePoiReader)
    private final ApachePoiReader reader = new ApachePoiReader();

    public static void main(String[] args) {
        SpringApplication.run(CapRotationBalanceApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        boolean exit = false;

        System.out.println("\n*************************************************");
        System.out.println("* HPE CAP ROTATION BALANCE - SPRING CONSOLE   *");
        System.out.println("*************************************************");

        while (!exit) {
            System.out.println("\n1. Leer archivo Excel (Raw Data)");
            System.out.println("2. Salir");
            System.out.print("Selecciona una opción: ");

            String option = scanner.nextLine();

            if (option.equals("1")) {
                System.out.print("Ingresa la ruta del archivo (.xlsx): ");
                String path = scanner.nextLine().replace("\"", "");

                File file = new File(path);
                if (file.exists() && !file.isDirectory()) {
                    processFile(file);
                } else {
                    System.err.println("Error: Archivo no encontrado.");
                }
            } else if (option.equals("2")) {
                exit = true;
                System.out.println("Saliendo de la aplicación...");
            }
        }
    }

    private void processFile(File file) {
        try (InputStream is = new FileInputStream(file)) {
            List<Map<String, String>> result = reader.readExcel(is);

            System.out.println("\n--- Resumen de Procesamiento ---");
            System.out.println("Total de filas leídas: " + result.size());

            if (!result.isEmpty()) {
                System.out.println("Primer registro detectado:");
                System.out.println(result.get(0));
            }
            System.out.println("--------------------------------");
        } catch (Exception e) {
            System.err.println("Falla al procesar Excel: " + e.getMessage());
        }
    }
}