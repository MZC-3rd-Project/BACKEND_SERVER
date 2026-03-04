package com.example.mediaworker.consumer;

import com.example.core.util.JsonUtils;
import com.example.mediaworker.dto.MediaEventMessage;
import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeTask;
import com.example.mediaworker.service.MediaDerivativeTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaConfirmedEventConsumer {

    private static final long DEFAULT_MEDIA_VERSION = 1L;

    private final MediaDerivativeTaskService mediaDerivativeTaskService;

    @KafkaListener(topics = "${media.worker.confirmed-topic:media.confirmed}", groupId = "${media.worker.group-id:media-worker-group}")
    @Transactional
    public void consume(String payload) {
        try {
            MediaEventMessage event = JsonUtils.fromJson(payload, MediaEventMessage.class);
            if (!isValid(event)) {
                log.warn("[MediaWorker] invalid event payload. payload={}", payload);
                return;
            }
            MediaDerivativeTask thumbnailTask = mediaDerivativeTaskService.enqueuePending(
                    event.getMediaId(),
                    normalizeMediaVersion(event.getMediaVersion()),
                    MediaDerivativeProfile.THUMBNAIL_WEBP,
                    event.getEventId()
            );
            MediaDerivativeTask displayTask = mediaDerivativeTaskService.enqueuePending(
                    event.getMediaId(),
                    normalizeMediaVersion(event.getMediaVersion()),
                    MediaDerivativeProfile.DISPLAY_WEBP,
                    event.getEventId()
            );
            log.info(
                    "[MediaWorker] confirmed event accepted. mediaId={}, thumbnailTaskId={}, displayTaskId={}, version={}, ownerType={}, ownerId={}, usageType={}, objectKey={}",
                    event.getMediaId(),
                    thumbnailTask.getId(),
                    displayTask.getId(),
                    thumbnailTask.getMediaVersion(),
                    event.getOwnerType(),
                    event.getOwnerId(),
                    event.getUsageType(),
                    event.getObjectKey()
            );
        } catch (Exception e) {
            log.error("[MediaWorker] event consume failed. payload={}", payload, e);
            throw e;
        }
    }

    private boolean isValid(MediaEventMessage event) {
        return event != null
                && event.getEventId() != null
                && event.getEventType() != null
                && event.getMediaId() != null;
    }

    private long normalizeMediaVersion(Long mediaVersion) {
        if (mediaVersion == null || mediaVersion < DEFAULT_MEDIA_VERSION) {
            return DEFAULT_MEDIA_VERSION;
        }
        return mediaVersion;
    }
}
