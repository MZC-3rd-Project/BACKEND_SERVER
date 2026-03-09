package com.example.mediaworker.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentRoutingJsonMessageConsumer;
import com.example.event.consumer.RouteSpec;
import com.example.event.inbox.InboxConsumerBinding;
import com.example.mediaworker.dto.MediaEventMessage;
import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeTask;
import com.example.mediaworker.service.MediaDerivativeTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@InboxConsumerBinding(consumerName = MediaConfirmedEventProcessor.CONSUMER_NAME)
public class MediaConfirmedEventProcessor extends AbstractIdempotentRoutingJsonMessageConsumer<MediaEventMessage> {

    public static final String CONSUMER_NAME = "media-worker-confirmed-events-consumer";
    private static final String IDEMPOTENT_EVENT_TYPE = "MEDIA_CONFIRMED_EVENT";
    private static final String ROUTE_KEY = "MEDIA_CONFIRMED";
    private static final long DEFAULT_MEDIA_VERSION = 1L;

    private final MediaDerivativeTaskService mediaDerivativeTaskService;
    private final Map<String, RouteSpec<MediaEventMessage>> routeSpecs;

    public MediaConfirmedEventProcessor(
            MediaDerivativeTaskService mediaDerivativeTaskService,
            IdempotentConsumerService idempotentConsumerService
    ) {
        super(idempotentConsumerService);
        this.mediaDerivativeTaskService = mediaDerivativeTaskService;
        this.routeSpecs = Map.of(ROUTE_KEY, RouteSpec.of(this::handleConfirmedEvent));
    }

    @Override
    protected Class<MediaEventMessage> payloadType() {
        return MediaEventMessage.class;
    }

    @Override
    protected String idempotentEventType() {
        return IDEMPOTENT_EVENT_TYPE;
    }

    @Override
    protected boolean hasRequiredPayload(MediaEventMessage event, String message) {
        if (event.getMediaId() == null) {
            log.warn("[MediaWorker] invalid event payload. payload={}", message);
            return false;
        }
        return true;
    }

    @Override
    protected void onMissingEnvelope(String message, MediaEventMessage event) {
        log.warn("[MediaWorker] invalid event payload. payload={}", message);
    }

    @Override
    protected void onProcessingException(String message, MediaEventMessage event, Exception exception) {
        log.error("[MediaWorker] event consume failed. payload={}", message, exception);
        throw propagate(exception);
    }

    @Override
    protected Map<String, RouteSpec<MediaEventMessage>> routeSpecs() {
        return routeSpecs;
    }

    @Override
    protected String normalizeEventType(String eventType) {
        return ROUTE_KEY;
    }

    private void handleConfirmedEvent(MediaEventMessage event) {
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
    }

    private long normalizeMediaVersion(Long mediaVersion) {
        if (mediaVersion == null || mediaVersion < DEFAULT_MEDIA_VERSION) {
            return DEFAULT_MEDIA_VERSION;
        }
        return mediaVersion;
    }
}
