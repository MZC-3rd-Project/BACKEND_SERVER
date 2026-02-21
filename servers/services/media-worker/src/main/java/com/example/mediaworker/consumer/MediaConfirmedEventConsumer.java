package com.example.mediaworker.consumer;

import com.example.core.util.JsonUtils;
import com.example.mediaworker.dto.MediaEventMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class MediaConfirmedEventConsumer {

    @KafkaListener(topics = "${media.worker.confirmed-topic:media.confirmed}", groupId = "${media.worker.group-id:media-worker-group}")
    @Transactional
    public void consume(String payload) {
        try {
            MediaEventMessage event = JsonUtils.fromJson(payload, MediaEventMessage.class);
            if (!isValid(event)) {
                log.warn("[MediaWorker] invalid event payload. payload={}", payload);
                return;
            }
            // TODO(#654, #656): 후속 워커 분리 시 ownerType 별 변환 파이프라인으로 라우팅한다.
            log.info(
                    "[MediaWorker] confirmed event received. mediaId={}, ownerType={}, ownerId={}, usageType={}, objectKey={}",
                    event.getMediaId(),
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
}
