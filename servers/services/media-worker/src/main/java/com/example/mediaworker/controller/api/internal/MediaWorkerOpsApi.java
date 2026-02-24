package com.example.mediaworker.controller.api.internal;

import com.example.api.response.ApiResponse;
import com.example.mediaworker.dto.internal.ops.request.MediaWorkerBackfillRequest;
import com.example.mediaworker.dto.internal.ops.request.MediaWorkerReplayRequest;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerBackfillResponse;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerDlqListResponse;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerReplayResponse;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerTaskSummaryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

public interface MediaWorkerOpsApi {

    @GetMapping("/tasks/summary")
    ApiResponse<MediaWorkerTaskSummaryResponse> taskSummary();

    @GetMapping("/tasks/dlq")
    ApiResponse<MediaWorkerDlqListResponse> recentDlqItems(
            @RequestParam(required = false) @Min(value = 1, message = "size must be positive") Integer size
    );

    @PostMapping("/tasks/{taskId}/replay")
    ApiResponse<MediaWorkerReplayResponse> replayTask(
            @PathVariable Long taskId,
            @Valid @RequestBody(required = false) MediaWorkerReplayRequest request
    );

    @PostMapping("/tasks/backfill")
    ApiResponse<MediaWorkerBackfillResponse> backfill(
            @Valid @RequestBody(required = false) MediaWorkerBackfillRequest request
    );
}
