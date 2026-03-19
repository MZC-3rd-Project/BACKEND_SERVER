package com.example.product.dto.item.response;

import com.example.product.entity.item.Item;
import com.example.product.service.query.detail.ItemCategoryDetailView;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record ItemSearchDocumentResponse(
        Long itemId,
        String title,
        String description,
        Long categoryId,
        String category,
        List<String> categoryPath,
        String domainType,
        String status,
        Long price,
        Long sellerId,
        Long storeId,
        Long thumbnailMediaId,
        List<String> tags,
        List<String> features,
        List<String> detailTitles,
        List<String> detailDescriptions,
        List<String> detailHighlights,
        LocalDateTime sourceCreatedAt,
        LocalDateTime sourceUpdatedAt
) {

    public ItemSearchDocumentResponse {
        categoryPath = immutableList(categoryPath);
        tags = immutableList(tags);
        features = immutableList(features);
        detailTitles = immutableList(detailTitles);
        detailDescriptions = immutableList(detailDescriptions);
        detailHighlights = immutableList(detailHighlights);
    }

    public static ItemSearchDocumentResponse from(
            Item item,
            ItemCategoryDetailView categoryDetail,
            ItemContentSnapshot contentSnapshot
    ) {
        ItemContentSnapshot safeContent = contentSnapshot == null ? ItemContentSnapshot.empty() : contentSnapshot;
        List<ItemDetailSectionResponse> detailSections = safeContent.detailSections();

        return new ItemSearchDocumentResponse(
                item.getId(),
                item.getTitle(),
                trimToNull(item.getDescription()),
                categoryDetail == null ? item.getCategoryId() : categoryDetail.categoryId(),
                categoryDetail == null ? null : trimToNull(categoryDetail.categoryName()),
                categoryDetail == null ? List.of() : categoryDetail.categoryPath(),
                item.getItemType().name(),
                item.getStatus().name(),
                item.getPrice(),
                item.getSellerId(),
                item.getStoreId(),
                item.getThumbnailMediaId(),
                safeContent.tags(),
                safeContent.features(),
                detailSections.stream()
                        .map(ItemDetailSectionResponse::getTitle)
                        .map(ItemSearchDocumentResponse::trimToNull)
                        .filter(Objects::nonNull)
                        .toList(),
                detailSections.stream()
                        .map(ItemDetailSectionResponse::getDescription)
                        .map(ItemSearchDocumentResponse::trimToNull)
                        .filter(Objects::nonNull)
                        .toList(),
                detailSections.stream()
                        .flatMap(section -> immutableList(section.getHighlights()).stream())
                        .map(ItemSearchDocumentResponse::trimToNull)
                        .filter(Objects::nonNull)
                        .toList(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private static List<String> immutableList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(ItemSearchDocumentResponse::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }
}
