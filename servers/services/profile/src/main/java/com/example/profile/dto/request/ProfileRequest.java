package com.example.profile.dto.request;

import com.example.core.id.jackson.SnowflakeId;
import com.example.profile.entity.Profiles;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProfileRequest {

    @SnowflakeId
    private Long mediaId;
    private String email;
    private String phone;
    private String delivery;
    private String nickname;

}
