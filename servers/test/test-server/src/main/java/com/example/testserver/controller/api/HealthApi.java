package com.example.testserver.controller.api;

import com.example.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Tag(name = "0. Health", description = "서버 상태 확인")
public interface HealthApi {

    @Operation(summary = "헬스 체크")
    @GetMapping("/health")
    ApiResponse<Map<String, String>> health();

    @Operation(summary = "공통 모듈 목록", description = "이 테스트 서버에서 검증 가능한 공통 모듈 목록")
    @GetMapping("/modules")
    ApiResponse<List<Map<String, String>>> listModules();
}
