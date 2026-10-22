package com.example.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import tk.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan(basePackages = "com.example.admin.mapper")
public class AdminServerApplication {

    public static void main(final String[] args) {
        SpringApplication.run(AdminServerApplication.class, args);
    }
}
