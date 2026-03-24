package com.example.search.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SearchItemEnrichmentPatchRequest(
        @NotBlank(message = "sourceHash는 필수입니다")
        @Size(max = 128, message = "sourceHash는 128자를 초과할 수 없습니다")
        String sourceHash,

        @NotBlank(message = "model은 필수입니다")
        @Size(max = 128, message = "model은 128자를 초과할 수 없습니다")
        String model,

        @NotBlank(message = "status는 필수입니다")
        @Size(max = 32, message = "status는 32자를 초과할 수 없습니다")
        String status,

        @Size(max = 10, message = "aiTags는 최대 10개까지 허용됩니다")
        List<String> aiTags,

        @Size(max = 15, message = "aiKeywords는 최대 15개까지 허용됩니다")
        List<String> aiKeywords,

        @Size(max = 500, message = "aiSummary는 500자를 초과할 수 없습니다")
        String aiSummary
) {
}
