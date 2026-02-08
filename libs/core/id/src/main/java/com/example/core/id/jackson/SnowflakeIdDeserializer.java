package com.example.core.id.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * String → Long 역직렬화.
 * JSON에서 문자열("1234567890123456789") 또는 숫자(1234567890123456789) 모두 Long으로 변환한다.
 */
public class SnowflakeIdDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        if (p.currentToken() == JsonToken.VALUE_STRING) {
            String text = p.getText().trim();
            if (text.isEmpty()) {
                return null;
            }
            return Long.parseLong(text);
        }
        return p.getLongValue();
    }
}
