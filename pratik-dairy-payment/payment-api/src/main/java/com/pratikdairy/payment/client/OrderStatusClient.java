package com.pratikdairy.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;

// Deliberately a small, local interface rather than depending on the whole order-api module -
// payment-service only needs this one call. Relies on the same auth-header-propagation pattern
// (payment-api/config/FeignClientConfig) as the rest of this codebase: this call carries
// forward the customer's own JWT from the original initiate()/verify() request, so it only
// works from a synchronous request path with an authenticated caller. NOT usable from the
// Razorpay webhook handler, which has no customer JWT to propagate - see
// PaymentServiceImpl.handleWebhook()'s comment.
@FeignClient(name = "PRATIK-DAIRY-ORDER", path = "orders", primary = false)
public interface OrderStatusClient {

    @PatchMapping(path = "internal/{orderId}/payment-result")
    void notifyPaymentResult(
            @PathVariable("orderId") String orderId,
            @RequestParam("status") String status,
            @RequestParam("transactionId") String transactionId
    );
}