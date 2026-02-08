package com.example.security.core.pii;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PII(개인식별정보) 필드를 표시하는 어노테이션.
 * 로깅 시 마스킹 처리가 필요한 필드에 사용한다.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PiiField {

    PiiType type() default PiiType.GENERAL;
}
