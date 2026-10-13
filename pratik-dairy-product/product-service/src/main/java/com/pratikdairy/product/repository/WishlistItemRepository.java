package com.pratikdairy.product.repository;

import com.pratikdairy.product.model.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, String> {

    List<WishlistItem> findByUsernameOrderByCreatedAtDesc(String username);

    boolean existsByUsernameAndProductId(String username, String productId);

    void deleteByUsernameAndProductId(String username, String productId);

    /** A product was deleted: it goes from everyone's wishlist too. */
    void deleteByProductId(String productId);
}
