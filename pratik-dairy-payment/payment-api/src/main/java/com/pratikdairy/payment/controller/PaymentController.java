package com.pratikdairy.payment.controller;

import com.pratikdairy.payment.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@ResponseBody
@FeignClient(name = "PRATIK-DAIRY-PAYMENT", path = "payments", primary = false)
public interface PaymentController {

    // Step 1 of checkout: create a local PENDING PaymentTransaction + a matching Razorpay
    // order, and hand the frontend what it needs to open Razorpay Checkout.
    @PostMapping(path = "initiate")
    ResponseEntity<InitiatePaymentResponse> initiate(@Valid @RequestBody InitiatePaymentRequest request);

    // Step 2: called once Razorpay Checkout returns a result to the browser. Verifies the
    // signature server-side (never trust the browser's word alone), marks the transaction
    // SUCCESS/FAILED, and notifies order-service.
    @PostMapping(path = "verify")
    ResponseEntity<PaymentTransactionDto> verify(@Valid @RequestBody VerifyPaymentRequest request);

    // Razorpay calls this directly (not the browser) for async events - payment captured after
    // the browser tab closed, a payment that failed post-redirect, refund processed, etc. No JWT
    // here; authenticity is proven by the webhook signature header instead - see
    // PaymentServiceImpl.handleWebhook() / gateway.verifyWebhookSignature().
    @PostMapping(path = "webhook/razorpay")
    ResponseEntity<Void> razorpayWebhook(@RequestBody String rawPayload,
                                         @RequestHeader("X-Razorpay-Signature") String signature);

    @GetMapping(path = "order/{orderId}")
    ResponseEntity<List<PaymentTransactionDto>> findByOrder(@PathVariable(name = "orderId") String orderId);

    // Refunds whatever was paid online for an order that has been cancelled, and closes any payment
    // still open on it. Called by order-service after a cancellation; safe to expose because it
    // re-reads the order and refuses unless it is really CANCELLED and belongs to the caller (or the
    // caller is an admin). Idempotent: a second call finds nothing left to refund.
    @PostMapping(path = "order/{orderId}/refund-cancelled")
    ResponseEntity<List<PaymentTransactionDto>> refundCancelledOrder(@PathVariable(name = "orderId") String orderId);

    // Service-to-service (order-service's unpaid-order sweep): is this order paid, or committed to cash on
    // delivery, and when did payment last move? Only a caller holding the internal key is let in.
    @PreAuthorize("isAuthenticated()")
    @GetMapping(path = "internal/order/{orderId}/summary")
    ResponseEntity<PaymentSummaryDto> orderSummary(@PathVariable(name = "orderId") String orderId);

    // Service-to-service: an order was cancelled, so close any payment attempt still open on it.
    @PreAuthorize("isAuthenticated()")
    @PostMapping(path = "internal/order/{orderId}/close-open")
    ResponseEntity<Void> closeOpenAttempts(@PathVariable(name = "orderId") String orderId);

    @GetMapping(path = "saved-methods")
    ResponseEntity<List<SavedPaymentMethodDto>> getSavedMethods();

    @DeleteMapping(path = "saved-methods/{id}")
    void deleteSavedMethod(@PathVariable(name = "id") String id);

    // Customer backed out of (or failed) the online payment sheet and chose "Place order
    // anyway" instead of retrying - switches this transaction to COD so the order still goes
    // through and gets collected at delivery. Only the transaction's own owner may do this
    // (enforced in PaymentServiceImpl, same as verify()); it's a customer action, not an
    // admin one, hence no @PreAuthorize here.
    @PostMapping(path = "{id}/convert-to-cod")
    ResponseEntity<PaymentTransactionDto> convertToCod(@PathVariable(name = "id") String id);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping(path = "admin/{id}/refund")
    ResponseEntity<PaymentTransactionDto> refund(@PathVariable(name = "id") String id,
                                                 @RequestBody RefundRequest request);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping(path = "admin/all")
    ResponseEntity<List<PaymentTransactionDto>> findAll();

    // COD has no gateway step, so nothing ever moves it out of PENDING automatically - an
    // admin has to confirm cash was actually collected at delivery. Only valid for COD
    // transactions currently in PENDING; see PaymentServiceImpl.markCodCollected().
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping(path = "admin/{id}/collect")
    ResponseEntity<PaymentTransactionDto> markCodCollected(@PathVariable(name = "id") String id);
}