package com.creditlens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CreditLensApplication {
    public static void main(String[] args) {
        SpringApplication.run(CreditLensApplication.class, args);
    }
}
