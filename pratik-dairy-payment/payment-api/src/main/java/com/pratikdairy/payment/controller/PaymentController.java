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

    @GetMapping(path = "saved-methods")
    ResponseEntity<List<SavedPaymentMethodDto>> getSavedMethods();

    @DeleteMapping(path = "saved-methods/{id}")
    void deleteSavedMethod(@PathVariable(name = "id") String id);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping(path = "admin/{id}/refund")
    ResponseEntity<PaymentTransactionDto> refund(@PathVariable(name = "id") String id,
                                                 @RequestBody RefundRequest request);

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping(path = "admin/all")
    ResponseEntity<List<PaymentTransactionDto>> findAll();
}