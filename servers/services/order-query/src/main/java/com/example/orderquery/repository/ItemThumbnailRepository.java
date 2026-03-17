package com.example.orderquery.repository;

import com.example.orderquery.entity.ItemThumbnail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemThumbnailRepository extends JpaRepository<ItemThumbnail, Long> {

    List<ItemThumbnail> findAllByItemId(Long itemId);

    Optional<ItemThumbnail> findFirstByItemId(Long itemId);
}
