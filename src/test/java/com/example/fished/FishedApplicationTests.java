package com.example.fished;


import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootTest
@MapperScan("com.example.fished.mapper")
@EnableScheduling
class FishedApplicationTests {

    public static void main(String[] args) {
        SpringApplication.run(FishedApplication.class,args);
    }
    @Test
    void contextLoads() {
    }

}
