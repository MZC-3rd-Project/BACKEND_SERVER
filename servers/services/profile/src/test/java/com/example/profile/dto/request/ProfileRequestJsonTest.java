package com.example.profile.dto.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileRequestJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesLegacyMediaReferenceFromStringMediaId() throws Exception {
        ProfileRequest request = objectMapper.readValue("""
                {
                  "mediaId": "media-4321"
                }
                """, ProfileRequest.class);

        assertThat(request.getMediaId()).isNull();
        assertThat(request.getMediaRef()).isEqualTo("media-4321");
    }

    @Test
    void rejectsUnsupportedMediaIdShape() {
        assertThatThrownBy(() -> objectMapper.readValue("""
                {
                  "mediaId": {
                    "unexpected": true
                  }
                }
                """, ProfileRequest.class))
                .isInstanceOf(JsonProcessingException.class);
    }
}
