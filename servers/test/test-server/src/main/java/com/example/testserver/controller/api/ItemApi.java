package com.example.testserver.controller.api;

import com.example.api.response.ApiResponse;
import com.example.testserver.dto.TestItemRequest;
import com.example.testserver.dto.TestItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "1. DB + Outbox", description = "BaseEntity, JPA Auditing, Outbox 패턴 + DTO @SnowflakeId 직렬화 테스트")
public interface ItemApi {

    @Operation(summary = "아이템 생성",
            description = "DB 저장 + Outbox 이벤트 발행. 응답 DTO의 id가 String으로 직렬화되는 것을 확인")
    @PostMapping("/items")
    ApiResponse<TestItemResponse> createItem(@RequestBody TestItemRequest request);

    @Operation(summary = "아이템 목록 조회",
            description = "모든 아이템 조회. 각 id가 Long이 아닌 String으로 응답되는 것을 확인")
    @GetMapping("/items")
    ApiResponse<List<TestItemResponse>> listItems();

    @Operation(summary = "아이템 단건 조회",
            description = "없는 ID로 조회 시 RESOURCE_NOT_FOUND 예외 발생 확인. id: String → Long 역직렬화")
    @GetMapping("/items/{id}")
    ApiResponse<TestItemResponse> getItem(@PathVariable Long id);
}
