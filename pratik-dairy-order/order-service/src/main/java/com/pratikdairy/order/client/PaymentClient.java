package com.pratikdairy.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

// Deliberately a small, local interface rather than depending on the payment-api module - the
// order service only needs these few calls. The refund call carries the caller's JWT (order-api's
// FeignClientConfig) and payment-service re-checks that the order really is CANCELLED, so it can't be
// used to get money back on a live order. The "internal" calls carry the shared internal key instead.
@FeignClient(name = "PRATIK-DAIRY-PAYMENT", contextId = "paymentClient", path = "payments",
        primary = false, url = "${payment.url}")
public interface PaymentClient {

    @PostMapping(path = "order/{orderId}/refund-cancelled")
    void refundCancelledOrder(@PathVariable("orderId") String orderId);

    /** Whether the order is paid or committed to cash on delivery, and when the last payment activity was. */
    @GetMapping(path = "internal/order/{orderId}/summary")
    PaymentSummaryDto summary(@PathVariable("orderId") String orderId);

    /** Closes any payment attempt still open on an order that has just been cancelled. */
    @PostMapping(path = "internal/order/{orderId}/close-open")
    void closeOpenAttempts(@PathVariable("orderId") String orderId);
}
