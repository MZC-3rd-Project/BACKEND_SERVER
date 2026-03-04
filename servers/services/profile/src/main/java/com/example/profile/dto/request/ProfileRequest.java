package com.example.profile.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.example.core.id.jackson.SnowflakeId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileRequest {

    @SnowflakeId
    private Long mediaId;
    private String email;
    private String phone;
    private String delivery;
    private String nickname;
    private String mediaRef;

    @JsonSetter("mediaId")
    public void setMediaIdRaw(Object mediaIdRaw) {
        if (mediaIdRaw == null) {
            this.mediaId = null;
            return;
        }

        if (mediaIdRaw instanceof Number number) {
            this.mediaId = number.longValue();
            return;
        }

        if (mediaIdRaw instanceof String value) {
            String normalized = value.trim();
            if (normalized.isEmpty()) {
                this.mediaId = null;
                return;
            }

            try {
                this.mediaId = Long.parseLong(normalized);
            } catch (NumberFormatException ignored) {
                this.mediaId = null;
                this.mediaRef = normalized;
            }
        }
    }
}
