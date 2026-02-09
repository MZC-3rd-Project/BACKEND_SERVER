package com.example.product.dto.performance;

import com.example.core.id.jackson.SnowflakeId;
import com.example.product.domain.performance.CastMember;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CastMemberResponse {

    @SnowflakeId
    private Long id;

    private String name;
    private String role;
    private String profileImageUrl;

    public static CastMemberResponse from(CastMember entity) {
        return CastMemberResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .role(entity.getRole())
                .profileImageUrl(entity.getProfileImageUrl())
                .build();
    }
}
