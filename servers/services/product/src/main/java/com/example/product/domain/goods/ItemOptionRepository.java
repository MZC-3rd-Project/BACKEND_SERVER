package com.example.product.domain.goods;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemOptionRepository extends JpaRepository<ItemOption, Long> {

    List<ItemOption> findByItemId(Long itemId);

    void deleteAllByItemId(Long itemId);
}
