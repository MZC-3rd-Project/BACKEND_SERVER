package com.example.product.service.command;

import com.example.core.exception.BusinessException;
import com.example.product.entity.category.Category;
import com.example.product.exception.ProductErrorCode;
import com.example.product.repository.CategoryRepository;
import com.example.product.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryCommandServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private CategoryCommandService categoryCommandService;

    @Test
    void delete_whenCategoryIsReferencedByItems_throwsConflict() {
        Long categoryId = 10L;
        Category category = Category.createRoot("Root", 1);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
        when(itemRepository.existsByCategoryId(categoryId)).thenReturn(true);

        assertThatThrownBy(() -> categoryCommandService.delete(categoryId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.CATEGORY_IN_USE);
    }

    @Test
    void delete_whenCategoryHasNoChildrenAndNoReferences_softDeletesCategory() {
        Long categoryId = 11L;
        Category category = Category.createRoot("Root", 1);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
        when(itemRepository.existsByCategoryId(categoryId)).thenReturn(false);

        categoryCommandService.delete(categoryId);

        verify(categoryRepository).findById(categoryId);
        verify(categoryRepository).existsByParentId(categoryId);
        verify(itemRepository).existsByCategoryId(categoryId);
    }
}
