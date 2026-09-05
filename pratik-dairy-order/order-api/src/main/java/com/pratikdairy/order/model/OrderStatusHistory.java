package com.pratikdairy.order.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pratikdairy.order.enums.OrderStatus;
import com.pratikdairy.parent.base.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

// Order previously only ever carried its CURRENT status - each update overwrote the last, so
// there was no way to see when an order moved from CONFIRMED to SHIPPED, or who changed it.
// This is an append-only log: one row per transition, so the full lifecycle is reconstructible.
// createdAt (from BaseEntity) doubles as "when this transition happened".
@Entity
@Table(name = "ORDER_STATUS_HISTORY")
@Data
@EqualsAndHashCode(callSuper = false)
public class OrderStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORDER_ID", nullable = false)
    @JsonIgnore
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private OrderStatus status;

    @Column(name = "REMARKS", columnDefinition = "VARCHAR(500)")
    private String remarks;

    // Username of whoever made the change (customer placing the order, or the admin updating it).
    @Column(name = "CHANGED_BY")
    private String changedBy;
}