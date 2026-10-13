package com.pratikdairy.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/** What order-service needs to know about an order's payments to tell whether it was abandoned. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSummaryDto {
    /** A payment succeeded (or was refunded), or the customer chose cash on delivery. */
    private boolean settledOrCod;
    /** When a payment attempt was last made or changed; null if payment was never started. */
    private ZonedDateTime lastActivityAt;
}
