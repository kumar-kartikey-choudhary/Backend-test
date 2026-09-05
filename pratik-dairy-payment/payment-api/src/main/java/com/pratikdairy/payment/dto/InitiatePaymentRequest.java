package com.pratikdairy.payment.dto;

import com.pratikdairy.payment.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InitiatePaymentRequest {
    @NotBlank(message = "orderId is required")
    private String orderId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "method is required")
    private PaymentMethod method;

    // If set, charge this saved card's gateway token directly instead of opening the
    // card-entry widget again. Ignored for UPI.
    private String savedPaymentMethodId;
}