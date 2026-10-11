package com.example.csvbatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class CsvBatchApplication {
    void main(String[] args) {
        var context = SpringApplication.run(CsvBatchApplication.class, args);
        System.exit(SpringApplication.exit(context));
    }
}
