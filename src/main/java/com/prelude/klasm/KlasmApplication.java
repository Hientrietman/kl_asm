package com.prelude.klasm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KlasmApplication {

    public static void main(String[] args) {
        SpringApplication.run(KlasmApplication.class, args);
    }
}
