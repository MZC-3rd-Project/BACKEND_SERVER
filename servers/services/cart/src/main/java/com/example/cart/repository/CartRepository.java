package com.example.cart.repository;

import com.example.cart.domain.CartLine;
import com.example.cart.domain.CartLineIdentity;
import java.util.List;

public interface CartRepository {

    List<CartLine> findAllByUserId(Long userId);

    void save(CartLine line, Long userId);

    void saveAll(List<CartLine> lines, Long userId);

    void delete(Long userId, CartLineIdentity identity);
}
