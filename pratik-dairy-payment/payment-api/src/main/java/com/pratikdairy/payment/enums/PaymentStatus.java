package com.pratikdairy.payment.enums;

public enum PaymentStatus {
    // A gateway order was created but the customer hasn't completed checkout yet.
    CREATED,
    // Customer submitted payment on the gateway's widget; awaiting our verify() call
    // or the gateway's webhook to confirm the outcome.
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED
}