package com.example.core.id.jackson;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Snowflake ID 필드에 붙이는 Jackson 직렬화 어노테이션.
 *
 * 내부적으로 Long으로 처리하되, JSON 직렬화 시 String으로 변환한다.
 * JavaScript에서 Number.MAX_SAFE_INTEGER(2^53 - 1)를 초과하는 Long 값의
 * 정밀도 손실을 방지하기 위함이다.
 *
 * 사용 예:
 *   @SnowflakeId
 *   private Long id;  // DB: 1234567890123456789 (long)
 *                      // JSON: "1234567890123456789" (string)
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SnowflakeIdSerializer.class)
@JsonDeserialize(using = SnowflakeIdDeserializer.class)
public @interface SnowflakeId {
}
