package com.example.search.document;

import com.example.search.client.dto.FundingCampaignSnapshot;
import com.example.search.client.dto.ProductSearchDocument;
import com.example.search.client.dto.StoreSnapshot;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record ItemDocument(
        Long itemId,
        String title,
        String description,
        Long categoryId,
        String category,
        List<String> categoryPath,
        List<String> categoryCodes,
        String domainType,
        String status,
        String salesChannel,
        Long price,
        Long basePrice,
        Long effectivePrice,
        Long sellerId,
        Long storeId,
        String storeName,
        Long thumbnailMediaId,
        String thumbnailUrl,
        List<String> tags,
        List<String> features,
        List<String> detailTitles,
        List<String> detailDescriptions,
        List<String> detailHighlights,
        List<String> aiTags,
        List<String> aiKeywords,
        String aiSummary,
        String aiSourceHash,
        String aiModel,
        String aiStatus,
        OffsetDateTime aiEnrichedAt,
        Integer stock,
        Integer availableStock,
        Long activeHotDealId,
        Long activeCampaignId,
        LocalDateTime sourceCreatedAt,
        LocalDateTime sourceUpdatedAt
) {

    public ItemDocument {
        categoryPath = immutableList(categoryPath);
        categoryCodes = immutableList(categoryCodes);
        tags = immutableList(tags);
        features = immutableList(features);
        detailTitles = immutableList(detailTitles);
        detailDescriptions = immutableList(detailDescriptions);
        detailHighlights = immutableList(detailHighlights);
        aiTags = immutableList(aiTags);
        aiKeywords = immutableList(aiKeywords);
    }

    public static ItemDocument from(
            ProductSearchDocument productDocument,
            StoreSnapshot storeSnapshot,
            Integer availableStock,
            FundingCampaignSnapshot fundingCampaignSnapshot
    ) {
        String status = trimToNull(productDocument.status());
        Long price = productDocument.price();
        String category = trimToNull(productDocument.category());
        List<String> categoryPath = productDocument.categoryPath();
        List<String> categoryCodes = SearchCategoryCodeResolver.resolve(
                category,
                categoryPath,
                fundingCampaignSnapshot == null ? null : fundingCampaignSnapshot.category()
        );

        return new ItemDocument(
                productDocument.itemId(),
                trimToNull(productDocument.title()),
                trimToNull(productDocument.description()),
                productDocument.categoryId(),
                category,
                categoryPath,
                categoryCodes,
                trimToNull(productDocument.domainType()),
                status,
                resolveSalesChannel(status),
                price,
                price,
                price,
                productDocument.sellerId(),
                productDocument.storeId(),
                storeSnapshot == null ? null : trimToNull(storeSnapshot.storeName()),
                productDocument.thumbnailMediaId(),
                null,
                productDocument.tags(),
                productDocument.features(),
                productDocument.detailTitles(),
                productDocument.detailDescriptions(),
                productDocument.detailHighlights(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                productDocument.stock(),
                availableStock,
                null,
                fundingCampaignSnapshot == null ? null : fundingCampaignSnapshot.campaignId(),
                productDocument.sourceCreatedAt(),
                productDocument.sourceUpdatedAt()
        );
    }

    private static List<String> immutableList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return values.stream()
                .map(ItemDocument::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String resolveSalesChannel(String status) {
        if (!StringUtils.hasText(status)) {
            return "NORMAL";
        }

        return switch (status.trim().toUpperCase()) {
            case "HOT_DEAL" -> "HOT_DEAL";
            case "FUNDING", "FUNDED", "FUND_FAILED" -> "FUNDING";
            default -> "NORMAL";
        };
    }

    private static String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }
}
