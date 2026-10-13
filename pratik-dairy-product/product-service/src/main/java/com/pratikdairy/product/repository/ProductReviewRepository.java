package com.pratikdairy.product.repository;

import com.pratikdairy.product.model.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, String> {

    List<ProductReview> findByProductIdOrderByCreatedAtDesc(String productId);

    Optional<ProductReview> findByProductIdAndUsername(String productId, String username);

    void deleteByProductId(String productId);

    /** One row per reviewed product: [productId, average rating, number of reviews]. */
    @Query("SELECT r.productId, AVG(r.rating), COUNT(r) FROM ProductReview r GROUP BY r.productId")
    List<Object[]> summarise();
}
