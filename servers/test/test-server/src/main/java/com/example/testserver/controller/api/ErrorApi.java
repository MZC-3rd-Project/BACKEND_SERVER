package com.example.testserver.controller.api;

import com.example.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "8. 예외 처리", description = "GlobalExceptionHandler 테스트")
public interface ErrorApi {

    @Operation(summary = "BusinessException 테스트", description = "WARN 로그 + 해당 HTTP 상태코드 반환")
    @GetMapping("/error/business")
    ApiResponse<Void> testBusinessError();

    @Operation(summary = "TechnicalException 테스트", description = "ERROR 로그 + 500 반환")
    @GetMapping("/error/technical")
    ApiResponse<Void> testTechnicalError();

    @Operation(summary = "RuntimeException 테스트", description = "GlobalExceptionHandler catch-all 확인")
    @GetMapping("/error/unexpected")
    ApiResponse<Void> testUnexpectedError();

    @Operation(summary = "DomainErrorCode 테스트 (커스텀 에러)",
            description = "서비스별 커스텀 에러 코드(DomainErrorCode) 확장 테스트. sealed interface의 non-sealed 확장 확인")
    @GetMapping("/error/domain/{errorType}")
    ApiResponse<Void> testDomainError(@PathVariable String errorType);
}
