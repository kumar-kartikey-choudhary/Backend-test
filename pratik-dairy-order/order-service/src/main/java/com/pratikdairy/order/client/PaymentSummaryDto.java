package com.pratikdairy.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.ZonedDateTime;

/** What payment-service says about an order's payments - just enough to decide whether it was abandoned. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentSummaryDto {
    /** A payment succeeded, or the customer chose cash on delivery: the order is committed, not abandoned. */
    private boolean settledOrCod;
    /** When the most recent payment attempt was made or changed; null if payment was never started. */
    private ZonedDateTime lastActivityAt;
}
