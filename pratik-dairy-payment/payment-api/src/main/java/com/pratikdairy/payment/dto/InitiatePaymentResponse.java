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
    // Set when a card is being saved or a saved card reused: Checkout must be opened with this customer
    // so it can show / store that customer's cards. Null otherwise.
    private String gatewayCustomerId;

    public InitiatePaymentResponse(String paymentTransactionId, String gatewayOrderId, String keyId,
                                   BigDecimal amount, String currency) {
        this(paymentTransactionId, gatewayOrderId, keyId, amount, currency, null);
    }
}