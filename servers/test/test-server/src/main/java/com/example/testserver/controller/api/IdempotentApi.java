package com.example.testserver.controller.api;

import com.example.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

@Tag(name = "5. 멱등성 테스트", description = "IdempotentConsumerService 중복 처리 방지 테스트")
public interface IdempotentApi {

    @Operation(summary = "멱등성 처리 테스트", description = "같은 eventId로 여러 번 호출하면 첫 번째만 처리됨")
    @PostMapping("/idempotent/{eventId}")
    ApiResponse<Map<String, Object>> testIdempotent(@PathVariable String eventId);
}
