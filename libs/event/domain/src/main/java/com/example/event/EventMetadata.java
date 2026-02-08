package com.example.event;

public record EventMetadata(
        String aggregateType,
        String aggregateId,
        String correlationId,
        String causationId
) {
    public static EventMetadata of(String aggregateType, String aggregateId) {
        return new EventMetadata(aggregateType, aggregateId, null, null);
    }

    public static EventMetadata of(String aggregateType, String aggregateId,
                                    String correlationId, String causationId) {
        return new EventMetadata(aggregateType, aggregateId, correlationId, causationId);
    }
}
