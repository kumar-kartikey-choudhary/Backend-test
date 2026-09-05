package com.pratikdairy.order.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pratikdairy.parent.base.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrderItems extends BaseEntity {

    @Column(name = "PRODUCT_ID" , nullable = false)
    private String productId;

    // In product-stockUnit multiples (e.g. 2.5 if the product is sold in "kg"), matching
    // Product.stockQuantity's type - there's no separate weight-variant to convert between
    // anymore, the product itself defines the unit.
    @Column(name = "QUANTITY", nullable = false)
    private BigDecimal quantity;

    @Column(name = "PRICE_AT_PURCHASE")
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;
}