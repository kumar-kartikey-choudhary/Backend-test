package com.pratikdairy.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * The few fields of order-service's order that payment-service needs. Deliberately not the full
 * order DTO (that would pull in the order-api module) - unknown fields are ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderSummaryDto {
    private String id;
    private String username;
    // Kept as text (PROCESSING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED) so this module needs no order enum.
    private String status;
    private BigDecimal totalAmount;
}
