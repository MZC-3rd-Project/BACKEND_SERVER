package com.example.testserver.controller.api;

import com.example.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "4. Redis 캐시", description = "RedisTemplate 캐시 CRUD 테스트")
public interface CacheApi {

    @Operation(summary = "캐시 저장", description = "TTL 5분으로 Redis에 값 저장")
    @PostMapping("/cache")
    ApiResponse<Void> cacheValue(@RequestBody Map<String, String> request);

    @Operation(summary = "캐시 조회")
    @GetMapping("/cache/{key}")
    ApiResponse<Object> getCachedValue(@PathVariable String key);

    @Operation(summary = "캐시 삭제")
    @DeleteMapping("/cache/{key}")
    ApiResponse<Boolean> deleteCachedValue(@PathVariable String key);
}
