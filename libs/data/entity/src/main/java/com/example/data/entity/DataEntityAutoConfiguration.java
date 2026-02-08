package com.example.data.entity;

import com.example.data.entity.config.JpaAuditingConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManagerFactory;

@AutoConfiguration
@ConditionalOnClass(EntityManagerFactory.class)
@Import(JpaAuditingConfig.class)
public class DataEntityAutoConfiguration {
}
