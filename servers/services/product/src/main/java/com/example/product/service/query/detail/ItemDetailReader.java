package com.example.product.service.query.detail;

import com.example.product.entity.item.Item;

import java.util.List;
import java.util.Map;

public interface ItemDetailReader<T> {

    T read(Item item);

    Map<Long, T> readAll(List<Item> items);
}
