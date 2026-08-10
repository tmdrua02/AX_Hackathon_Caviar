package com.haneul.medassist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class MedAssistApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedAssistApplication.class, args);
    }
}
