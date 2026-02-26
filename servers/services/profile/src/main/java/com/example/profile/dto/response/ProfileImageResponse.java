package com.example.profile.dto.response;

import com.example.core.id.jackson.SnowflakeId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileImageResponse {
    @SnowflakeId private long userId;
    @SnowflakeId private long mediaId;
}
