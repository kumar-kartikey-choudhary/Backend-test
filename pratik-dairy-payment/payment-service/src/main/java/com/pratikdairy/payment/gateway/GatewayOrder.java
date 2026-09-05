package com.pratikdairy.payment.gateway;

public record GatewayOrder(String id, String currency, long amountInSubunits) {
}