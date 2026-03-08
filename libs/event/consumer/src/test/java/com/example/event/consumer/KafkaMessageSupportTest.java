package com.example.event.consumer;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaMessageSupportTest {

    @Test
    void normalizePayload_keepsStringPayload() {
        String message = "{\"eventId\":\"evt-1\",\"eventType\":\"UserCreated\"}";

        String normalized = KafkaMessageSupport.normalizePayload(message);

        assertThat(normalized).isEqualTo(message);
    }

    @Test
    void normalizePayload_andReadJsonEnvelope_extractsEventMetadata() {
        Map<String, Object> payload = Map.of(
                "eventId", "evt-2",
                "eventType", "UserEmailChanged",
                "userId", 101L
        );

        String normalized = KafkaMessageSupport.normalizePayload(payload);
        JsonEventEnvelope envelope = KafkaMessageSupport.readJsonEnvelope(normalized);

        assertThat(envelope.getEventId()).isEqualTo("evt-2");
        assertThat(envelope.getEventType()).isEqualTo("UserEmailChanged");
    }

    @Test
    void normalizePayload_unwrapsStructuredJsonString() {
        String message = """
                "{\\"eventId\\":\\"evt-3\\",\\"eventType\\":\\"ITEM_CREATED\\",\\"itemId\\":101}"
                """;

        String normalized = KafkaMessageSupport.normalizePayload(message);
        JsonEventEnvelope envelope = KafkaMessageSupport.readJsonEnvelope(normalized);

        assertThat(normalized).isEqualTo("{\"eventId\":\"evt-3\",\"eventType\":\"ITEM_CREATED\",\"itemId\":101}");
        assertThat(envelope.getEventId()).isEqualTo("evt-3");
        assertThat(envelope.getEventType()).isEqualTo("ITEM_CREATED");
    }

    @Test
    void normalizePayload_decodesByteArrayPayload() {
        byte[] payload = "{\"eventId\":\"evt-4\",\"eventType\":\"ITEM_STATUS_CHANGED\"}".getBytes();

        String normalized = KafkaMessageSupport.normalizePayload(payload);
        JsonEventEnvelope envelope = KafkaMessageSupport.readJsonEnvelope(normalized);

        assertThat(normalized).isEqualTo("{\"eventId\":\"evt-4\",\"eventType\":\"ITEM_STATUS_CHANGED\"}");
        assertThat(envelope.getEventId()).isEqualTo("evt-4");
        assertThat(envelope.getEventType()).isEqualTo("ITEM_STATUS_CHANGED");
    }
}
