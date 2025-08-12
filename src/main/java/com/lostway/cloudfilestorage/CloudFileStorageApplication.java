package com.lostway.cloudfilestorage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@Slf4j
@ComponentScan(basePackages = {
        "com.lostway.cloudfilestorage",
        "com.lostway.jwtsecuritylib"
})
public class CloudFileStorageApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudFileStorageApplication.class, args);
        log.info("Application started on http://localhost:8088/swagger-ui/index.html");
    }

}
