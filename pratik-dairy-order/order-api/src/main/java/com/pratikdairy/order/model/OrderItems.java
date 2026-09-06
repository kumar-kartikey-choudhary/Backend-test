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

    // Number of packages of the given `weight` purchased (e.g. 3, when weight = "250g") - a
    // package count, NOT a physical stock-unit amount. Paired with `price` (per-package price),
    // quantity * price gives this line's subtotal. The physical amount actually deducted from
    // Product.stockQuantity at checkout is a separate figure (CartItemDto.stockToConsume) that
    // isn't stored here - it only matters at the moment of the stock decrement call.
    @Column(name = "QUANTITY", nullable = false)
    private BigDecimal quantity;

    // Price per single package of the selected weight (already weight-adjusted from the
    // product's base per-stockUnit price), not the base per-stockUnit price itself.
    @Column(name = "PRICE_AT_PURCHASE")
    private BigDecimal price;

    // The weight/package size selected at purchase time, e.g. "250g", "500g", "1kg". Recorded
    // here (not just left in the cart) so the order/receipt always shows what was actually
    // bought, even after the cart line itself is long gone.
    @Column(name = "WEIGHT", columnDefinition = "VARCHAR(20)")
    private String weight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;
}