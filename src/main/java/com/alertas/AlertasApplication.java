package com.alertas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AlertasApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertasApplication.class, args);
    }
}
