package com.pratikdairy.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Everything Razorpay Checkout hands back to the browser on success. The signature is what lets
// payment-service prove this response genuinely came from Razorpay and wasn't forged by a
// tampered client - see PaymentGatewayClient.verifySignature().
@Data
public class VerifyPaymentRequest {
    @NotBlank
    private String paymentTransactionId;
    @NotBlank
    private String razorpayOrderId;
    @NotBlank
    private String razorpayPaymentId;
    @NotBlank
    private String razorpaySignature;

    // Only meaningful for CARD payments - if true, and the gateway payment was made with a new
    // card, payment-service asks Razorpay to tokenize it and stores the resulting token as a
    // SavedPaymentMethod. No raw card data ever passes through this field or this service.
    private boolean saveCard;
}