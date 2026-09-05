package com.pratikdairy.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundRequest {
    // Null = full refund of the original amount.
    private BigDecimal amount;
    private String reason;
}