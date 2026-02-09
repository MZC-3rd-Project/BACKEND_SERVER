package com.example.product.service;

import com.example.core.exception.BusinessException;
import com.example.product.domain.category.Category;
import com.example.product.domain.category.CategoryRepository;
import com.example.product.dto.category.CategoryCreateRequest;
import com.example.product.dto.category.CategoryTreeResponse;
import com.example.product.dto.category.CategoryUpdateRequest;
import com.example.product.exception.ProductErrorCode;
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
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public Category create(CategoryCreateRequest request) {
        if (request.getParentId() == null) {
            return categoryRepository.save(
                    Category.createRoot(request.getName(), request.getSortOrder()));
        }

        Category parent = categoryRepository.findById(request.getParentId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));

        if (parent.getDepth() >= 2) {
            throw new BusinessException(ProductErrorCode.CATEGORY_DEPTH_EXCEEDED);
        }

        return categoryRepository.save(
                Category.createChild(request.getName(), parent.getId(), parent.getDepth(), request.getSortOrder()));
    }

    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
    }

    @Transactional
    public Category update(Long id, CategoryUpdateRequest request) {
        Category category = findById(id);
        category.update(request.getName(), request.getSortOrder());
        return category;
    }

    @Transactional
    public void delete(Long id) {
        Category category = findById(id);

        if (categoryRepository.existsByParentId(id)) {
            throw new BusinessException(ProductErrorCode.CATEGORY_HAS_CHILDREN);
        }

        category.softDelete();
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
