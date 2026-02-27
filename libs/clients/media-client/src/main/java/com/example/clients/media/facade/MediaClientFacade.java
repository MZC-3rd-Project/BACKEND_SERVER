package com.example.clients.media.facade;

import com.example.clients.media.dto.MediaLinksSyncCommand;

import java.util.List;
import java.util.Map;

/**
 * Internal media API facade shared by domain services.
 *
 * Endpoints:
 * GET /internal/v1/media/{mediaId}/url
 * POST /internal/v1/media/urls/batch
 * PUT /internal/v1/media/links/sync
 */
public interface MediaClientFacade {

    /**
     * Returns a readable URL for one media id.
     *
     * Response example:
     * {
     *   "success": true,
     *   "data": {
     *     "mediaId": 101,
     *     "status": "CONFIRMED",
     *     "mediaUrl": "https://cdn.example.com/101.jpg"
     *   }
     * }
     */
    String getMediaUrl(Long mediaId);

    /**
     * Returns mediaId -> mediaUrl map for batch ids.
     *
     * Request example:
     * {
     *   "mediaIds": [101, 102, 103]
     * }
     */
    Map<Long, String> getMediaUrlMap(List<Long> mediaIds);

    /**
     * Synchronizes final media links for one owner aggregate.
     *
     * Request example:
     * {
     *   "ownerType": "ITEM",
     *   "ownerId": 280995533466779648,
     *   "sets": [
     *     { "usageType": "THUMBNAIL", "mediaIds": [101] },
     *     { "usageType": "GALLERY", "mediaIds": [102, 103] }
     *   ]
     * }
     */
    void syncLinks(MediaLinksSyncCommand command);
}
