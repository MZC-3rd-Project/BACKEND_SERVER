package com.example.product.service.content;

import com.example.product.dto.item.request.ItemDetailSectionRequest;
import com.example.product.dto.item.response.ItemContentSnapshot;
import com.example.product.dto.item.response.ItemDetailSectionResponse;
import com.example.product.entity.item.ItemDetailSection;
import com.example.product.entity.item.ItemFeature;
import com.example.product.entity.item.ItemTag;
import com.example.product.repository.ItemDetailSectionRepository;
import com.example.product.repository.ItemFeatureRepository;
import com.example.product.repository.ItemTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ItemContentService {

    private final ItemTagRepository itemTagRepository;
    private final ItemFeatureRepository itemFeatureRepository;
    private final ItemDetailSectionRepository itemDetailSectionRepository;
    private final ProductMediaUrlNormalizer productMediaUrlNormalizer;

    @Transactional
    public void replaceTags(Long itemId, List<String> tags) {
        itemTagRepository.softDeleteAllByItemId(itemId);
        List<String> normalized = normalizeStrings(tags);
        if (normalized.isEmpty()) {
            return;
        }
        List<ItemTag> entities = IntStream.range(0, normalized.size())
                .mapToObj(index -> ItemTag.create(itemId, normalized.get(index), index))
                .toList();
        itemTagRepository.saveAll(entities);
    }

    @Transactional
    public void replaceFeatures(Long itemId, List<String> features) {
        itemFeatureRepository.softDeleteAllByItemId(itemId);
        List<String> normalized = normalizeStrings(features);
        if (normalized.isEmpty()) {
            return;
        }
        List<ItemFeature> entities = IntStream.range(0, normalized.size())
                .mapToObj(index -> ItemFeature.create(itemId, normalized.get(index), index))
                .toList();
        itemFeatureRepository.saveAll(entities);
    }

    @Transactional
    public void replaceDetailSections(Long itemId, List<ItemDetailSectionRequest> detailSections) {
        itemDetailSectionRepository.softDeleteAllByItemId(itemId);
        if (detailSections == null || detailSections.isEmpty()) {
            return;
        }
        List<ItemDetailSection> entities = IntStream.range(0, detailSections.size())
                .mapToObj(index -> toEntity(itemId, detailSections.get(index), index))
                .filter(Objects::nonNull)
                .toList();
        if (!entities.isEmpty()) {
            itemDetailSectionRepository.saveAll(entities);
        }
    }

    @Transactional
    public void softDeleteAll(Long itemId) {
        itemTagRepository.softDeleteAllByItemId(itemId);
        itemFeatureRepository.softDeleteAllByItemId(itemId);
        itemDetailSectionRepository.softDeleteAllByItemId(itemId);
    }

    @Transactional(readOnly = true)
    public ItemContentSnapshot findByItemId(Long itemId) {
        List<String> tags = itemTagRepository.findByItemIdOrderBySortOrderAsc(itemId).stream()
                .map(ItemTag::getTagName)
                .toList();
        List<String> features = itemFeatureRepository.findByItemIdOrderBySortOrderAsc(itemId).stream()
                .map(ItemFeature::getFeatureText)
                .toList();
        List<ItemDetailSectionResponse> detailSections = itemDetailSectionRepository.findByItemIdOrderBySortOrderAsc(itemId)
                .stream()
                .map(this::toResponse)
                .toList();
        return new ItemContentSnapshot(tags, features, detailSections);
    }

    @Transactional(readOnly = true)
    public Map<Long, ItemContentSnapshot> findByItemIds(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }

        List<Long> distinctItemIds = itemIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctItemIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> tagsMap = itemTagRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(distinctItemIds).stream()
                .collect(Collectors.groupingBy(
                        ItemTag::getItemId,
                        Collectors.mapping(ItemTag::getTagName, Collectors.toList())));

        Map<Long, List<String>> featuresMap = itemFeatureRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(distinctItemIds).stream()
                .collect(Collectors.groupingBy(
                        ItemFeature::getItemId,
                        Collectors.mapping(ItemFeature::getFeatureText, Collectors.toList())));

        Map<Long, List<ItemDetailSectionResponse>> sectionsMap = itemDetailSectionRepository
                .findByItemIdInOrderByItemIdAscSortOrderAsc(distinctItemIds).stream()
                .collect(Collectors.groupingBy(
                        ItemDetailSection::getItemId,
                        Collectors.mapping(this::toResponse, Collectors.toList())));

        Map<Long, ItemContentSnapshot> result = new LinkedHashMap<>();
        for (Long itemId : distinctItemIds) {
            result.put(itemId, new ItemContentSnapshot(
                    tagsMap.getOrDefault(itemId, List.of()),
                    featuresMap.getOrDefault(itemId, List.of()),
                    sectionsMap.getOrDefault(itemId, List.of())));
        }
        return result;
    }

    private ItemDetailSection toEntity(Long itemId, ItemDetailSectionRequest request, int sortOrder) {
        if (request == null) {
            return null;
        }
        String title = trimToNull(request.getTitle());
        String description = trimToNull(request.getDescription());
        String imageUrl = trimToNull(request.getImageUrl());
        List<String> highlights = normalizeStrings(request.getHighlights());

        if (title == null && description == null && imageUrl == null && highlights.isEmpty()) {
            return null;
        }
        return ItemDetailSection.create(itemId, title, description, imageUrl, highlights, sortOrder);
    }

    private ItemDetailSectionResponse toResponse(ItemDetailSection detailSection) {
        return ItemDetailSectionResponse.builder()
                .title(detailSection.getTitle())
                .description(detailSection.getDescription())
                .imageUrl(productMediaUrlNormalizer.normalize(detailSection.getImageUrl()))
                .highlights(detailSection.getHighlights())
                .build();
    }

    private List<String> normalizeStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }
}
