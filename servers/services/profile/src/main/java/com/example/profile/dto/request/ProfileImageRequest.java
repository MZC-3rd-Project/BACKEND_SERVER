package com.example.profile.dto.request;

import com.example.core.id.jackson.SnowflakeId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProfileImageRequest {
    @SnowflakeId private Long userId;
    @SnowflakeId private Long mediaId;


}
