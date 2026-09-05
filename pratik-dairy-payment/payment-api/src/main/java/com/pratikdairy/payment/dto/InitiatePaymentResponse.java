package com.pratikdairy.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Everything the frontend's Razorpay Checkout widget needs, and nothing more. Note keyId is
// Razorpay's PUBLIC key (safe to expose to the browser, by design) - the secret key never
// leaves payment-service.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePaymentResponse {
    private String paymentTransactionId; // our internal PaymentTransaction.id
    private String gatewayOrderId;       // Razorpay order_id
    private String keyId;                // Razorpay public key, for Checkout.js
    private BigDecimal amount;
    private String currency;
}