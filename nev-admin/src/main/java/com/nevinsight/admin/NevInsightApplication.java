package com.nevinsight.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.nevinsight")
@MapperScan("com.nevinsight.model.mapper")
@EnableCaching
@EnableRetry
@EnableAsync
@EnableScheduling
public class NevInsightApplication {

    public static void main(String[] args) {
        SpringApplication.run(NevInsightApplication.class, args);
    }
}
