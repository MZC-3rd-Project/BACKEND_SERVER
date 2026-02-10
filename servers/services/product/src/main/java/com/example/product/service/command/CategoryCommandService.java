package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.product.dto.category.request.CategoryCreateRequest;
import com.example.product.dto.category.request.CategoryUpdateRequest;
import com.example.product.entity.category.Category;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryCommandService {

    private final CategoryRepository categoryRepository;

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

    public Category update(Long id, CategoryUpdateRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
        category.update(request.getName(), request.getSortOrder());
        return category;
    }

    public void delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));

        if (categoryRepository.existsByParentId(id)) {
            throw new BusinessException(ProductErrorCode.CATEGORY_HAS_CHILDREN);
        }

        category.softDelete();
    }
}
