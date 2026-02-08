package com.example.core.id.jpa;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Snowflake ID 생성을 위한 JPA 어노테이션.
 *
 * @GeneratedValue 대신 이 어노테이션을 사용하면
 * Hibernate가 자동으로 SnowflakeIdentifierGenerator를 사용한다.
 *
 * 사용 예:
 *   @Id
 *   @SnowflakeGenerated
 *   private Long id;
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@IdGeneratorType(SnowflakeIdentifierGenerator.class)
public @interface SnowflakeGenerated {
}
