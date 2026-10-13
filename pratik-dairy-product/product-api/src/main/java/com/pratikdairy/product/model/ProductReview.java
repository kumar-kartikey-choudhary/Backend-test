package com.pratikdairy.product.model;

import com.pratikdairy.parent.base.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** A customer's star rating and optional comment on a product. One per (customer, product); editing replaces it. */
@Entity
@Table(name = "PRODUCT_REVIEW", uniqueConstraints = @UniqueConstraint(columnNames = {"USERNAME", "PRODUCT_ID"}))
@Data
@EqualsAndHashCode(callSuper = false)
public class ProductReview extends BaseEntity {

    @Column(name = "PRODUCT_ID", nullable = false)
    private String productId;

    @Column(name = "USERNAME", nullable = false)
    private String username;

    @Column(name = "RATING", nullable = false)
    private int rating;

    @Column(name = "COMMENT_TEXT", columnDefinition = "VARCHAR(1000)")
    private String comment;
}
