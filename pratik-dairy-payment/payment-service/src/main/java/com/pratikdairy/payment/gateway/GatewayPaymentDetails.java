package com.pratikdairy.payment.gateway;

import com.pratikdairy.payment.enums.PaymentMethod;

// method-specific fields are null when not applicable (e.g. cardLastFour is null for a UPI payment).
public record GatewayPaymentDetails(
        String gatewayPaymentId,
        PaymentMethod method,
        String status,
        String upiVpa,
        String cardLastFour,
        String cardNetwork
) {
}