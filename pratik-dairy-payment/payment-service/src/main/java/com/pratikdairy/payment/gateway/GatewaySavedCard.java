package com.pratikdairy.payment.gateway;

public record GatewaySavedCard(
        String tokenId,
        String cardLastFour,
        String cardNetwork,
        Integer expiryMonth,
        Integer expiryYear
) {
}