package com.hpe.cap_rotation_balance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class CapRotationBalanceApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(CapRotationBalanceApplication.class)
                .headless(false) // ESTO ES LO MÁS IMPORTANTE
                .run(args);
    }
}