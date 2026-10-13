package com.pratikdairy.product.model;

import com.pratikdairy.parent.base.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** A product a customer has saved for later. One row per (customer, product). */
@Entity
@Table(name = "WISHLIST_ITEM", uniqueConstraints = @UniqueConstraint(columnNames = {"USERNAME", "PRODUCT_ID"}))
@Data
@EqualsAndHashCode(callSuper = false)
public class WishlistItem extends BaseEntity {

    @Column(name = "USERNAME", nullable = false)
    private String username;

    @Column(name = "PRODUCT_ID", nullable = false)
    private String productId;
}
