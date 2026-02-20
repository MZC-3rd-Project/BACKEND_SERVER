package com.example.search.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.util.JsonUtils;
import com.example.search.exception.SearchErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Component
public class SearchCursorCodec {

    public String encode(List<Object> sortValues) {
        if (sortValues == null || sortValues.isEmpty()) {
            return null;
        }

        String json = JsonUtils.toJson(sortValues);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public List<Object> decode(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return List.of();
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor.trim());
            String json = new String(decoded, StandardCharsets.UTF_8);
            return JsonUtils.fromJson(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BusinessException(SearchErrorCode.INVALID_SEARCH_PARAMETER, "유효하지 않은 cursor 값입니다.", e);
        }
    }
}
