package com.xiaoxj.server;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com")
public class K8sMcpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(K8sMcpServerApplication.class, args);
    }
}