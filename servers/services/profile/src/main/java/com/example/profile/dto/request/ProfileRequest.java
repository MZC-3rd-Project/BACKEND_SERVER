package com.example.profile.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileRequest {

    private Long mediaId;
    private String email;
    private String phone;
    private String delivery;
    private String nickname;
    private String mediaRef;

    @JsonSetter("phone")
    public void setPhoneRaw(String phone) {
        this.phone = phone;
    }

    @JsonSetter("nickname")
    public void setNicknameRaw(String nickname) {
        this.nickname = nickname;
    }

    @JsonSetter("mediaId")
    public void setMediaIdRaw(Object mediaIdRaw) {
        if (mediaIdRaw == null) {
            this.mediaId = null;
            this.mediaRef = null;
            return;
        }

        if (mediaIdRaw instanceof Number number) {
            this.mediaId = number.longValue();
            this.mediaRef = null;
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
                this.mediaRef = null;
            } catch (NumberFormatException ignored) {
                this.mediaId = null;
                this.mediaRef = normalized;
            }
            return;
        }

        throw new IllegalArgumentException("mediaId must be a number or string");
    }
}
