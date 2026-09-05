package com.pratikdairy.order.dto;

import com.pratikdairy.order.enums.OrderStatus;
import com.pratikdairy.parent.base.dto.BaseDto;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderResponse extends BaseDto {
    private String username;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private String couponCode; // null if no coupon was applied
    private BigDecimal totalAmount;
    private OrderStatus status;
    private LocalDateTime orderDateTime = LocalDateTime.now();
    private List<OrderItemDto> items;
    // Payment results show up as entries here (via the payment-service callback - see
    // OrderServiceImpl.recordPaymentResult()), not as a separate payments list - actual payment
    // records (transaction ids, gateway details) live in payment-service, not order-service.
    private List<OrderStatusHistoryDto> statusHistory = new ArrayList<>();

    // Used by create()/updateStatus() for the customer's own view - no explicit date needed there
    public OrderResponse(String id, String username, OrderStatus status,
                         BigDecimal subtotalAmount, BigDecimal discountAmount, String couponCode,
                         @NotNull BigDecimal totalAmount, List<OrderItemDto> items) {
        this.setId(id);
        this.username = username;
        this.status = status;
        this.subtotalAmount = subtotalAmount;
        this.discountAmount = discountAmount;
        this.couponCode = couponCode;
        this.totalAmount = totalAmount;
        this.items = items;
    }

    // Used by findAll()/findByCustomerName() - carries the real order date plus status history,
    // needed for the admin table and the customer's order detail view.
    public OrderResponse(String id, String username, LocalDateTime orderDateTime, OrderStatus status,
                         BigDecimal subtotalAmount, BigDecimal discountAmount, String couponCode,
                         @NotNull BigDecimal totalAmount, List<OrderItemDto> items,
                         List<OrderStatusHistoryDto> statusHistory) {
        this.setId(id);
        this.username = username;
        this.orderDateTime = orderDateTime;
        this.status = status;
        this.subtotalAmount = subtotalAmount;
        this.discountAmount = discountAmount;
        this.couponCode = couponCode;
        this.totalAmount = totalAmount;
        this.items = items;
        this.statusHistory = statusHistory;
    }
}