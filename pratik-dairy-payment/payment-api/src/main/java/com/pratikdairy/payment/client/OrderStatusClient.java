package com.pratikdairy.payment.client;

import com.pratikdairy.payment.dto.OrderSummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
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

    // The order as the order service sees it (status, total, owner). Read with the internal key rather
    // than the caller's token, so it also works where there is no customer token - the Razorpay webhook.
    // Who may act on the order is decided by payment-service itself, from the owner in the answer.
    @GetMapping(path = "internal/{orderId}/summary")
    OrderSummaryDto findOrderSummary(@PathVariable("orderId") String orderId);

    @PatchMapping(path = "internal/{orderId}/payment-result")
    void notifyPaymentResult(
            @PathVariable("orderId") String orderId,
            @RequestParam("status") String status,
            @RequestParam("transactionId") String transactionId
    );
}