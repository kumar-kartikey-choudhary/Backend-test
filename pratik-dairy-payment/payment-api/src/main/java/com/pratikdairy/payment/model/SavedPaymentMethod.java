package com.pratikdairy.payment.model;

import com.pratikdairy.parent.base.entity.BaseEntity;
import com.pratikdairy.payment.enums.CardNetwork;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

// A "saved card" is a reference to a token the GATEWAY holds, not a card we hold. Razorpay (or
// Stripe, etc.) tokenizes the card on their side during a successful payment and hands back an
// opaque token id; charging it again later means sending that token back to the gateway, which
// looks the real card up on its own PCI-compliant vault. Every field here is either that token
// or non-sensitive display metadata the gateway returns alongside it.
//
// Fields intentionally absent, and that must NEVER be added: card number (PAN), CVV, full
// expiry as a parsed date usable for fraud, cardholder name tied to the PAN. last4 + network +
// expiry month/year is the standard "safe to display" subset every PCI-DSS guide allows.
@Entity
@Table(name = "SAVED_PAYMENT_METHOD")
@Data
@EqualsAndHashCode(callSuper = false)
public class SavedPaymentMethod extends BaseEntity {

    @Column(name = "USERNAME", nullable = false)
    private String username;

    // Razorpay's customer id - required to look up/reuse tokens under their Customer API.
    @Column(name = "GATEWAY_CUSTOMER_ID", nullable = false)
    private String gatewayCustomerId;

    // The opaque token id itself. This is what actually gets charged - treat it as sensitive
    // (don't log it, don't return it to the frontend), even though it's useless outside our
    // gateway account.
    @Column(name = "GATEWAY_TOKEN_ID", nullable = false, unique = true)
    private String gatewayTokenId;

    @Column(name = "CARD_LAST_FOUR", columnDefinition = "VARCHAR(4)")
    private String cardLastFour;

    @Enumerated(EnumType.STRING)
    @Column(name = "CARD_NETWORK")
    private CardNetwork cardNetwork;

    @Column(name = "CARD_EXPIRY_MONTH")
    private Integer cardExpiryMonth;

    @Column(name = "CARD_EXPIRY_YEAR")
    private Integer cardExpiryYear;

    @Column(name = "IS_DEFAULT", columnDefinition = "TINYINT(1) DEFAULT '0'")
    private boolean isDefault = false;
}