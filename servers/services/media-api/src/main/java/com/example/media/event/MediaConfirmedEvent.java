package com.example.media.event;

import com.example.event.DomainEvent;
import com.example.media.entity.MediaOwnerType;
import com.example.media.entity.MediaUsageType;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class MediaConfirmedEvent extends DomainEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final long DEFAULT_MEDIA_VERSION = 1L;

    private final Long mediaId;
    private final Long uploaderId;
    private final String bucketName;
    private final String objectKey;
    private final String contentType;
    private final Long fileSize;
    private final String etag;
    private final MediaOwnerType ownerType;
    private final Long ownerId;
    private final MediaUsageType usageType;
    private final Integer sortOrder;
    private final String mediaUrl;
    private final Long mediaVersion;

    public MediaConfirmedEvent(Long mediaId,
                               Long uploaderId,
                               String bucketName,
                               String objectKey,
                               String contentType,
                               Long fileSize,
                               String etag,
                               MediaOwnerType ownerType,
                               Long ownerId,
                               MediaUsageType usageType,
                               Integer sortOrder,
                               String mediaUrl) {
        super("media.confirmed");
        this.mediaId = mediaId;
        this.uploaderId = uploaderId;
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.etag = etag;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.usageType = usageType;
        this.sortOrder = sortOrder;
        this.mediaUrl = mediaUrl;
        this.mediaVersion = DEFAULT_MEDIA_VERSION;
    }

    @Override
    public String getEventTypeName() {
        return "MEDIA_CONFIRMED";
    }

    @Override
    public Map<String, Object> getPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("mediaId", mediaId);
        payload.put("uploaderId", uploaderId);
        payload.put("bucketName", bucketName);
        payload.put("objectKey", objectKey);
        payload.put("contentType", contentType);
        payload.put("fileSize", fileSize);
        payload.put("etag", etag);
        payload.put("ownerType", ownerType != null ? ownerType.name() : null);
        payload.put("ownerId", ownerId);
        payload.put("usageType", usageType != null ? usageType.name() : null);
        payload.put("sortOrder", sortOrder);
        payload.put("mediaUrl", mediaUrl);
        payload.put("mediaVersion", mediaVersion);
        return payload;
    }
}
