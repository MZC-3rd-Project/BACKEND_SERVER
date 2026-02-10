package com.example.product.service.query;

import com.example.core.exception.BusinessException;
import com.example.product.dto.category.response.CategoryTreeResponse;
import com.example.product.entity.category.Category;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryQueryService {

    private final CategoryRepository categoryRepository;

    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
    }

    public List<CategoryTreeResponse> getTree() {
        List<Category> all = categoryRepository.findAllByOrderByDepthAscSortOrderAsc();
        return buildTree(all);
    }

    private List<CategoryTreeResponse> buildTree(List<Category> categories) {
        Map<Long, CategoryTreeResponse> nodeMap = new LinkedHashMap<>();
        List<CategoryTreeResponse> roots = new ArrayList<>();

        for (Category cat : categories) {
            CategoryTreeResponse node = CategoryTreeResponse.from(cat);
            nodeMap.put(cat.getId(), node);

            if (cat.getParentId() == null) {
                roots.add(node);
            } else {
                CategoryTreeResponse parent = nodeMap.get(cat.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }

        return roots;
    }
}
