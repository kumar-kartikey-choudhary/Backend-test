package com.pratikdairy.payment.dto;

import com.pratikdairy.parent.base.dto.BaseDto;
import com.pratikdairy.payment.enums.PaymentMethod;
import com.pratikdairy.payment.enums.PaymentStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentTransactionDto extends BaseDto {
    private String orderId;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod method;
    private PaymentStatus status;
    // gatewayPaymentId is included - it's a safe, non-sensitive reference id (not the same as
    // a saved-card token, which is never returned to the frontend).
    private String gatewayPaymentId;
    private String upiVpa;
    private String failureReason;
    private LocalDateTime paidAt;
}