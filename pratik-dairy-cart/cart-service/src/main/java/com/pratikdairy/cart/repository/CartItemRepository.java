package com.pratikdairy.cart.repository;

import com.pratikdairy.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, String> {
    List<CartItem> findByUsername(String username);

    CartItem findByUsernameAndProductId(String username, String productId);

    void deleteByUsernameAndProductId(String username, String productId);

    void deleteByUsername(String username);
}