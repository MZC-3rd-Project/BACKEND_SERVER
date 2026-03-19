package com.example.funding.service.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClosingSoonCampaignCachePayload {

    private LocalDateTime refreshedAt;

    @Builder.Default
    private List<ClosingSoonCampaignSnapshot> items = new ArrayList<>();
}
