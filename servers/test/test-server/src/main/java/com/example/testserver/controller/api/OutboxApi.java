package com.example.testserver.controller.api;

import com.example.api.response.ApiResponse;
import com.example.event.outbox.OutboxMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "2. Outbox 관리", description = "Outbox 메시지 상태 조회")
public interface OutboxApi {

    @Operation(summary = "전체 Outbox 메시지 조회")
    @GetMapping("/outbox")
    ApiResponse<List<OutboxMessage>> getAllOutbox();

    @Operation(summary = "상태별 Outbox 메시지 조회", description = "status: PENDING, PUBLISHED, FAILED")
    @GetMapping("/outbox/{status}")
    ApiResponse<List<OutboxMessage>> getOutboxByStatus(@PathVariable String status);
}
