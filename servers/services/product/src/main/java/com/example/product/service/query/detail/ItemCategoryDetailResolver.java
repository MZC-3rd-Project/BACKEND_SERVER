package com.example.product.service.query.detail;

import com.example.product.entity.category.Category;
import com.example.product.entity.item.Item;
import com.example.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ItemCategoryDetailResolver {

    private final CategoryRepository categoryRepository;

    public Map<Long, ItemCategoryDetailView> resolve(List<Item> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }

        boolean hasCategory = items.stream().anyMatch(item -> item.getCategoryId() != null);
        if (!hasCategory) {
            return Map.of();
        }

        Map<Long, Category> categoryMap = categoryRepository.findAllByOrderByDepthAscSortOrderAsc().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        Map<Long, ItemCategoryDetailView> resolved = new LinkedHashMap<>();
        for (Item item : items) {
            if (item.getCategoryId() == null) {
                continue;
            }
            Category category = categoryMap.get(item.getCategoryId());
            if (category == null) {
                continue;
            }
            resolved.put(item.getId(), new ItemCategoryDetailView(
                    category.getId(),
                    category.getName(),
                    buildCategoryPath(category, categoryMap)
            ));
        }
        return resolved;
    }

    private List<String> buildCategoryPath(Category category, Map<Long, Category> categoryMap) {
        LinkedList<String> path = new LinkedList<>();
        Category current = category;
        while (current != null) {
            path.addFirst(current.getName());
            Long parentId = current.getParentId();
            current = parentId != null ? categoryMap.get(parentId) : null;
        }
        return List.copyOf(path);
    }
}
