package com.example.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import tk.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan(basePackages = "com.example.erp.mapper")
public class ErpApplication {

    public static void main(final String[] args) {
        SpringApplication.run(ErpApplication.class, args);
    }
}