package com.example.testserver.dto;

import com.example.core.id.jackson.SnowflakeId;
import com.example.testserver.domain.TestItem;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * TestItem 응답 DTO.
 *
 * @SnowflakeId를 id 필드에 붙이면
 * 내부적으로 Long인 Snowflake ID가 JSON에서 String으로 직렬화된다.
 *
 * 응답 예시:
 *   "id": "1234567890123456789"  ← String (JS 정밀도 손실 방지)
 *   "name": "테스트 아이템"
 */
@Getter
@Builder
public class TestItemResponse {

    @SnowflakeId
    private Long id;

    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TestItemResponse from(TestItem entity) {
        return TestItemResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
