package com.pratikdairy.order.service.impl;

import com.pratikdairy.order.client.PaymentClient;
import com.pratikdairy.order.client.PaymentSummaryDto;
import com.pratikdairy.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * Gives back the stock held by orders nobody paid for.
 *
 * Checkout reserves stock straight away, whatever the payment method. A customer who places an order
 * and then abandons the payment (closes the tab, never finishes UPI) would otherwise hold that stock
 * for ever. Every few minutes this looks at orders older than the grace period that are still
 * CONFIRMED and asks payment-service whether each was paid or committed to cash on delivery; the ones
 * that were neither - and show no recent payment activity - are cancelled.
 *
 * If payment-service can't be reached an order is left alone: not knowing is never a reason to cancel.
 * (With several order-service instances each would run the sweep; the work is idempotent, so that is
 * only wasted effort, not a problem.)
 */
@Slf4j
@Component
public class UnpaidOrderExpiryJob {

    private final OrderService orderService;
    private final PaymentClient paymentClient;

    @Value("${order.unpaid-expiry-minutes:30}")
    private long expiryMinutes = 30;

    @Autowired
    public UnpaidOrderExpiryJob(OrderService orderService, PaymentClient paymentClient) {
        this.orderService = orderService;
        this.paymentClient = paymentClient;
    }

    @Scheduled(initialDelayString = "${order.expiry-scan-initial-delay-ms:60000}",
            fixedDelayString = "${order.expiry-scan-interval-ms:300000}")
    public void run() {
        try {
            int expired = sweep();
            if (expired > 0) {
                log.info("Unpaid-order sweep cancelled {} order(s)", expired);
            }
        } catch (RuntimeException e) {
            log.error("Unpaid-order sweep failed: {}", e.getMessage());
        }
    }

    /** @return how many orders were cancelled */
    public int sweep() {
        LocalDateTime placedBefore = LocalDateTime.now().minusMinutes(expiryMinutes);
        ZonedDateTime activeSince = ZonedDateTime.now().minusMinutes(expiryMinutes);
        int expired = 0;
        for (String orderId : orderService.findUnpaidCandidateIds(placedBefore)) {
            try {
                PaymentSummaryDto payments = paymentClient.summary(orderId);
                if (payments.isSettledOrCod()) {
                    continue; // paid, or the customer chose cash on delivery
                }
                if (payments.getLastActivityAt() != null && payments.getLastActivityAt().isAfter(activeSince)) {
                    continue; // a payment attempt is still recent - the customer may be mid-payment
                }
                if (orderService.expireUnpaid(orderId)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not check order {} for expiry, leaving it alone: {}", orderId, e.getMessage());
            }
        }
        return expired;
    }
}
