package com.example.fished;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.fished.mapper")
public class FishedApplication {

    public static void main(String[] args) {

        SpringApplication.run(FishedApplication.class, args);
    }

}
