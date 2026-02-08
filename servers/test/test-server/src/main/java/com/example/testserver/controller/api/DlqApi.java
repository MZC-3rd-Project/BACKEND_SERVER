package com.example.testserver.controller.api;

import com.example.api.response.ApiResponse;
import com.example.config.kafka.DeadLetterMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Tag(name = "3. DLQ 관리", description = "Dead Letter Queue 메시지 조회")
public interface DlqApi {

    @Operation(summary = "전체 DLQ 메시지 조회", description = "처리 실패한 Kafka 메시지 목록")
    @GetMapping("/dlq")
    ApiResponse<List<DeadLetterMessage>> getAllDeadLetters();
}
