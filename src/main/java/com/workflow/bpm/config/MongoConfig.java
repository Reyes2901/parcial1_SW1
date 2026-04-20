package com.workflow.bpm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoAuditing  // 👈 Habilita @CreatedDate y @LastModifiedDate
@EnableMongoRepositories(basePackages = "com.workflow.bpm")
public class MongoConfig {
    // La configuración de conexión se toma de application.yml
}