package com.example.mediaworker.dto;

import com.example.event.consumer.EventEnvelope;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MediaEventMessage implements EventEnvelope {

    private Integer schemaVersion;
    private String eventId;
    private String eventType;
    private Long mediaId;
    private Long uploaderId;
    private String bucketName;
    private String objectKey;
    private String contentType;
    private Long fileSize;
    private String etag;
    private String ownerType;
    private Long ownerId;
    private String usageType;
    private Integer sortOrder;
    private String mediaUrl;
    private Long mediaVersion;
}
