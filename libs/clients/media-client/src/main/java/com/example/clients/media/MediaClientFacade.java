package com.example.clients.media;

import java.util.List;
import java.util.Map;

public interface MediaClientFacade {

    String getMediaUrl(Long mediaId);

    Map<Long, String> getMediaUrlMap(List<Long> mediaIds);

    void syncLinks(MediaLinksSyncCommand command);
}
