package com.example.testserver.controller.api;

import com.example.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@Tag(name = "9. Snowflake ID", description = "Snowflake ID 생성기 테스트 — 내부 long, 외부 String 직렬화")
public interface SnowflakeApi {

    @Operation(summary = "Snowflake ID 단건 생성",
            description = "Long으로 생성 후 String으로 반환. JavaScript Number 정밀도 손실 방지 확인")
    @GetMapping("/snowflake/generate")
    ApiResponse<Map<String, Object>> generateId();

    @Operation(summary = "Snowflake ID 다건 생성",
            description = "10개의 ID를 연속 생성하여 단조 증가(monotonic) 확인")
    @GetMapping("/snowflake/generate/batch")
    ApiResponse<List<Map<String, Object>>> generateBatchIds();

    @Operation(summary = "Snowflake ID 파싱",
            description = "ID에서 생성 시각, datacenterId, workerId, sequence를 추출")
    @GetMapping("/snowflake/parse/{id}")
    ApiResponse<Map<String, Object>> parseId(@PathVariable String id);
}
