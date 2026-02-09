package com.example.product.domain.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByParentIdOrderBySortOrder(Long parentId);

    List<Category> findByParentIdIsNullOrderBySortOrder();

    List<Category> findAllByOrderByDepthAscSortOrderAsc();

    boolean existsByParentId(Long parentId);
}
