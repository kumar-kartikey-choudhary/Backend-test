package com.pratikdairy.payment.model;

import com.pratikdairy.parent.base.entity.BaseEntity;
import com.pratikdairy.payment.enums.PaymentMethod;
import com.pratikdairy.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One row per payment attempt against an order. A retried/failed-then-successful payment is
// two rows, not one row overwritten - same audit-trail principle used for InventoryLedger and
// OrderStatusHistory elsewhere in this codebase.
//
// CRITICAL: this table (and this whole service) never stores a card PAN, CVV, or expiry.
// Card entry happens entirely on the gateway's own hosted checkout/SDK; all we ever receive
// back is a gateway-issued id (order id, payment id) and, for saved cards, a token - see
// SavedPaymentMethod. That keeps this service (and this database) out of PCI-DSS scope.
@Entity
@Table(name = "PAYMENT_TRANSACTION")
@Data
@EqualsAndHashCode(callSuper = false)
public class PaymentTransaction extends BaseEntity {

    // order-service's Order.id - a plain string reference, not a FK, since Order lives in a
    // different microservice's database (same cross-service pattern used throughout this app).
    @Column(name = "ORDER_ID", nullable = false)
    private String orderId;

    @Column(name = "USERNAME", nullable = false)
    private String username;

    @Column(name = "AMOUNT", nullable = false)
    private BigDecimal amount;

    @Column(name = "CURRENCY", columnDefinition = "VARCHAR(3) DEFAULT 'INR'")
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "METHOD", columnDefinition = "VARCHAR(15) NOT NULL", nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", columnDefinition = "VARCHAR(10) NOT NULL", nullable = false)
    private PaymentStatus status = PaymentStatus.CREATED;

    // Razorpay's order_id (created up front) and payment_id (assigned once the customer pays).
    @Column(name = "GATEWAY_ORDER_ID")
    private String gatewayOrderId;

    @Column(name = "GATEWAY_PAYMENT_ID")
    private String gatewayPaymentId;

    // Set if the customer paid via UPI - the gateway's payer handle (e.g. "name@bank"), safe to
    // store since it's not a secret the way card data is.
    @Column(name = "UPI_VPA")
    private String upiVpa;

    // If this payment used (or created) a saved card, links to it. Null for one-off payments.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SAVED_PAYMENT_METHOD_ID")
    private SavedPaymentMethod savedPaymentMethod;

    @Column(name = "FAILURE_REASON", columnDefinition = "VARCHAR(500)")
    private String failureReason;

    @Column(name = "PAID_AT")
    private LocalDateTime paidAt;
}