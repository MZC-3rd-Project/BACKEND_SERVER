package com.example.profile.dto.response;

import com.example.profile.entity.ProfilesImage;
import org.springframework.context.annotation.Profile;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * DTO for {@link com.example.profile.entity.ProfilesImage}
 */
public record ProfilesImageResponse(LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt,
                                    Long profileId, String mediaId, Integer sortOrder) {

    public static ProfilesImageResponse of(ProfilesImage profilesImage) {
        return new ProfilesImageResponse(
            profilesImage.getCreatedAt(),
            profilesImage.getUpdatedAt(),
            profilesImage.getDeletedAt(),
            profilesImage.getProfileId(),
            profilesImage.getMediaId(),
            profilesImage.getSortOrder()
        );
    }
}
