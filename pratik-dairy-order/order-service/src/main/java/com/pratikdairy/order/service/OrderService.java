package com.pratikdairy.order.service;

import com.pratikdairy.order.dto.OrderResponse;
import com.pratikdairy.order.enums.OrderStatus;

import java.util.List;

public interface OrderService {

    OrderResponse create(String couponCode);

    List<OrderResponse> findAll();

    OrderResponse updateStatus(String id, OrderStatus status, String remarks);

    void delete(String id);

    List<OrderResponse> findByCustomerName();

    // Called by payment-service's callback once a payment attempt resolves - appends an
    // ORDER_STATUS_HISTORY entry noting the payment outcome, without changing order.status.
    void recordPaymentResult(String orderId, String paymentStatus, String transactionId);
}