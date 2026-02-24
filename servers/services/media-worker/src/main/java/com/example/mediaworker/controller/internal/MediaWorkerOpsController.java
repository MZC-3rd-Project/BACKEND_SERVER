package com.example.mediaworker.controller.internal;

import com.example.api.response.ApiResponse;
import com.example.mediaworker.controller.api.internal.MediaWorkerOpsApi;
import com.example.mediaworker.dto.internal.ops.request.MediaWorkerBackfillRequest;
import com.example.mediaworker.dto.internal.ops.request.MediaWorkerReplayRequest;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerBackfillResponse;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerDlqListResponse;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerReplayResponse;
import com.example.mediaworker.dto.internal.ops.response.MediaWorkerTaskSummaryResponse;
import com.example.mediaworker.entity.MediaDerivativeTask;
import com.example.mediaworker.service.ops.MediaWorkerOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/v1/media-worker")
@RequiredArgsConstructor
public class MediaWorkerOpsController implements MediaWorkerOpsApi {

    private final MediaWorkerOpsService mediaWorkerOpsService;

    @Override
    public ApiResponse<MediaWorkerTaskSummaryResponse> taskSummary() {
        return ApiResponse.success(mediaWorkerOpsService.getTaskSummary());
    }

    @Override
    public ApiResponse<MediaWorkerDlqListResponse> recentDlqItems(Integer size) {
        return ApiResponse.success(mediaWorkerOpsService.getRecentDlqItems(size));
    }

    @Override
    public ApiResponse<MediaWorkerReplayResponse> replayTask(Long taskId, MediaWorkerReplayRequest request) {
        String reason = request == null ? null : request.getReason();
        MediaDerivativeTask replayedTask = mediaWorkerOpsService.replayFailedTask(taskId, reason);
        MediaWorkerReplayResponse response = MediaWorkerReplayResponse.builder()
                .taskId(replayedTask.getId())
                .queued(true)
                .previousStatus("FAILED")
                .currentStatus(replayedTask.getStatus().name())
                .message("replay queued")
                .build();
        return ApiResponse.success(response);
    }

    @Override
    public ApiResponse<MediaWorkerBackfillResponse> backfill(MediaWorkerBackfillRequest request) {
        return ApiResponse.success(mediaWorkerOpsService.backfill(request));
    }
}
