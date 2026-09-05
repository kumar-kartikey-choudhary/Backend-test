package com.pratikdairy.order.model;

import com.pratikdairy.order.enums.DiscountType;
import com.pratikdairy.parent.base.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Was entirely missing before - an order had a totalAmount but no way to apply a discount to
// it. This is a standard promo-code table: a code, how the discount is computed, and the usage
// limits that keep it from being applied outside its intended window.
@Entity
@Table(name = "COUPON")
@Data
@EqualsAndHashCode(callSuper = false)
public class Coupon extends BaseEntity {

    @Column(name = "CODE", columnDefinition = "VARCHAR(30) NOT NULL", unique = true, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "DISCOUNT_TYPE", columnDefinition = "VARCHAR(10) NOT NULL", nullable = false)
    private DiscountType discountType;

    // For PERCENT: a whole-number-ish percentage (e.g. 10 = 10% off).
    // For FLAT: a currency amount taken straight off the subtotal.
    @Column(name = "DISCOUNT_VALUE", nullable = false)
    private BigDecimal discountValue;

    // Order subtotal must be at least this much for the coupon to apply. Null = no minimum.
    @Column(name = "MIN_ORDER_VALUE")
    private BigDecimal minOrderValue;

    // Caps a PERCENT discount so "20% off" can't blow up on a huge order. Ignored for FLAT.
    @Column(name = "MAX_DISCOUNT")
    private BigDecimal maxDiscount;

    @Column(name = "VALID_FROM")
    private LocalDateTime validFrom;

    @Column(name = "VALID_TO")
    private LocalDateTime validTo;

    // Total number of times this code can ever be redeemed, across all customers. Null = unlimited.
    @Column(name = "USAGE_LIMIT")
    private Integer usageLimit;

    @Column(name = "USED_COUNT", columnDefinition = "INT DEFAULT '0'")
    private int usedCount = 0;

    @Column(name = "IS_ACTIVE", columnDefinition = "TINYINT(1) DEFAULT '1'")
    private boolean active = true;
}