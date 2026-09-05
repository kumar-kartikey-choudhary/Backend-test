package com.pratikdairy.payment.gateway;

import java.math.BigDecimal;

// Abstracts the payment gateway so PaymentServiceImpl doesn't depend on Razorpay specifics
// directly - swapping to Cashfree/Stripe later means writing one new implementation of this
// interface, not touching PaymentServiceImpl.
public interface PaymentGatewayClient {

    /** Creates an order on the gateway's side, up front, before the customer pays. */
    GatewayOrder createOrder(BigDecimal amount, String currency, String receipt);

    /**
     * Verifies that a (gatewayOrderId, gatewayPaymentId, signature) triple genuinely came from
     * the gateway and wasn't forged by a tampered client. This is the step that actually proves
     * a payment succeeded - never trust the browser's "payment succeeded" callback alone.
     */
    boolean verifySignature(String gatewayOrderId, String gatewayPaymentId, String signature);

    /** Verifies an incoming webhook call's signature against the configured webhook secret. */
    boolean verifyWebhookSignature(String rawPayload, String signatureHeader);

    /** Fetches payment method details (UPI VPA, card network/last4) for display + saved-card creation. */
    GatewayPaymentDetails fetchPayment(String gatewayPaymentId);

    /** Tokenizes the card used in a successful payment for reuse later. Returns null if the
     *  gateway/customer doesn't support tokenization for this payment. */
    GatewaySavedCard tokenizeCard(String gatewayCustomerId, String gatewayPaymentId);

    /** Creates (or returns the existing) gateway-side customer id for a given username, needed
     *  before a card can be tokenized against that customer. */
    String getOrCreateGatewayCustomer(String username);

    GatewayRefund refund(String gatewayPaymentId, BigDecimal amount);
}