package com.example.security.core.pii;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback PatternLayout을 확장하여 로그 메시지에서 PII를 자동 마스킹한다.
 *
 * logback-spring.xml 설정:
 * <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
 *     <layout class="com.example.security.core.pii.PiiMaskingPatternLayout">
 *         <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
 *     </layout>
 * </encoder>
 */
public class PiiMaskingPatternLayout extends PatternLayout {

    @Override
    public String doLayout(ILoggingEvent event) {
        String message = super.doLayout(event);
        return PiiMaskingUtils.maskLogMessage(message);
    }
}
