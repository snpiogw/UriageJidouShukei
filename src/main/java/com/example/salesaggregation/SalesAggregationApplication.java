package com.example.salesaggregation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SalesAggregationApplication {
    public static void main(String[] args) {
        SpringApplication.run(SalesAggregationApplication.class, args);
    }
}
