package com.intervia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class InterviaApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviaApplication.class, args);
    }
}
