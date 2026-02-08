package com.example.testserver.controller;

import com.example.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Hidden
@RestController
@RequestMapping("/internal")
public class InternalController {

    @GetMapping("/slow")
    public ApiResponse<Map<String, String>> slowEndpoint(@RequestParam(defaultValue = "5000") long delayMs) throws InterruptedException {
        Thread.sleep(delayMs);
        return ApiResponse.success(Map.of("delayed", delayMs + "ms"));
    }

    @GetMapping("/fail")
    public ApiResponse<Void> failEndpoint() {
        throw new RuntimeException("Intentional failure for circuit breaker test");
    }
}
