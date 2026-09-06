package com.pratikdairy.order.model;

import com.pratikdairy.order.enums.OrderStatus;
import com.pratikdairy.parent.base.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "ORDER_HEADER")
@Data
@EqualsAndHashCode(callSuper = true)
public class Order extends  BaseEntity{


    @Column(name = "ORDER_TIME", nullable = false)
    private LocalDateTime orderDateTime = LocalDateTime.now();


    @Column(name = "USERNAME", nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "ORDER_STATUS" , nullable = false)
    private OrderStatus status = OrderStatus.PROCESSING;

    // Sum of orderItems' (price * quantity), before any coupon discount.
    @NotNull
    @Column(name = "SUBTOTAL_AMOUNT")
    private BigDecimal subtotalAmount;

    // How much the applied coupon knocked off subtotalAmount. Zero when no coupon was used.
    @Column(name = "DISCOUNT_AMOUNT", columnDefinition = "DECIMAL(10,2) DEFAULT '0.00'")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    // subtotalAmount - discountAmount. Was the only amount tracked before; still here so
    // existing readers of "totalAmount" keep working.
    @NotNull
    @Column(name = "TOTAL_AMOUNT")
    private BigDecimal totalAmount;

    // Nullable - most orders won't use a coupon. Same-service FK, unlike the cross-service
    // references elsewhere, since Coupon lives in this service's own database.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COUPON_ID")
    private Coupon coupon;

    // Payment records for this order live in the standalone payment-service
    // (PaymentTransaction, keyed by orderId), not here - order-service only finds out about a
    // payment result via the internal payment-result callback (see OrderController /
    // OrderServiceImpl.recordPaymentResult()), which appends to statusHistory below rather than
    // duplicating payment state in this table.

    @NotNull
    @OneToMany(cascade = CascadeType.ALL , mappedBy = "order",orphanRemoval = true)
    private List<OrderItems> items  = new ArrayList<>();

    // Was: status lived only as the single ORDER_STATUS column above, with no history of past
    // transitions. See OrderStatusHistory.
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "order", orphanRemoval = true)
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

}