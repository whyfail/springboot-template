package com.wl.cwa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CwaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CwaApplication.class, args);
    }
}
