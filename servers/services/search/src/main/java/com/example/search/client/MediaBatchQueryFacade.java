package com.example.search.client;

import java.util.List;
import java.util.Map;

/**
 * Internal media batch query facade for search service.
 *
 * Endpoint:
 * POST /internal/v1/media/urls/batch
 *
 * Request example:
 * {
 *   "mediaIds": [11, 12, 13]
 * }
 *
 * Response example:
 * {
 *   "success": true,
 *   "data": [
 *     { "mediaId": 11, "mediaUrl": "https://cdn.example.com/11.jpg" },
 *     { "mediaId": 12, "mediaUrl": "https://cdn.example.com/12.jpg" }
 *   ]
 * }
 */
public interface MediaBatchQueryFacade {

    /**
     * Returns mediaId -> mediaUrl map for indexing view composition.
     */
    Map<Long, String> fetchMediaUrlMap(List<Long> mediaIds);
}
