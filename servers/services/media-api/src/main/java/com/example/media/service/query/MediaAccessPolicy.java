package com.example.media.service.query;

import com.example.core.exception.BusinessException;
import com.example.media.entity.MediaFile;
import com.example.media.exception.MediaErrorCode;
import org.springframework.stereotype.Component;

@Component
public class MediaAccessPolicy {

    public void validateReadAccess(MediaFile mediaFile, MediaAccessContext accessContext) {
        if (accessContext == null || accessContext.isInternal()) {
            return;
        }

        if (accessContext.isAuthenticated()
            && mediaFile.getUploaderId() != null
            && !mediaFile.isOwnedBy(accessContext.userId())) {
            throw new BusinessException(MediaErrorCode.FORBIDDEN_MEDIA_ACCESS);
        }
    }
}
