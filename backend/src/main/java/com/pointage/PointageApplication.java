package com.pointage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PointageApplication {

    public static void main(String[] args) {
        SpringApplication.run(PointageApplication.class, args);
    }
}
