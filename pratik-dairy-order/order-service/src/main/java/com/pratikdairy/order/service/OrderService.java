package com.pratikdairy.order.service;

import com.pratikdairy.order.dto.DeliveryAddressDto;
import com.pratikdairy.order.dto.OrderResponse;
import com.pratikdairy.order.enums.OrderStatus;

import java.util.List;

public interface OrderService {

    OrderResponse create(String couponCode, DeliveryAddressDto deliveryAddress);

    // Customer-initiated cancellation of their own order, only before it ships.
    OrderResponse cancel(String id);

    // A single order; only its owner or an admin may read it.
    OrderResponse findById(String id);

    // The same, with product names/images and the status history (for the invoice page).
    OrderResponse findDetail(String id);

    List<OrderResponse> findAll();

    /** Ids of CONFIRMED orders placed before the cutoff - the sweep then checks whether each was ever paid. */
    List<String> findUnpaidCandidateIds(java.time.LocalDateTime olderThan);

    /**
     * Cancels an order that was never paid (stock and coupon go back). Returns false, changing nothing,
     * if it is no longer CONFIRMED by the time this runs.
     */
    boolean expireUnpaid(String orderId);

    /** One order, with no ownership check - for service-to-service callers only. */
    OrderResponse findForService(String id);

    OrderResponse updateStatus(String id, OrderStatus status, String remarks);

    void delete(String id);

    List<OrderResponse> findByCustomerName();

    // Called by payment-service's callback once a payment attempt resolves - appends an
    // ORDER_STATUS_HISTORY entry noting the payment outcome, without changing order.status.
    void recordPaymentResult(String orderId, String paymentStatus, String transactionId);
}