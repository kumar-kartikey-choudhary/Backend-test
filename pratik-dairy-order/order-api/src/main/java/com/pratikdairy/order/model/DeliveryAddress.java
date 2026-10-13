package com.pratikdairy.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot of where an order is to be delivered, copied onto the order at checkout. Deliberately
 * not a reference to the user-service Address row: the customer may later edit or delete that
 * address, and the order must still show where it was actually sent.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAddress {

    @Column(name = "DELIVERY_LABEL", length = 50)
    private String label;

    @Column(name = "DELIVERY_LINE1", length = 255)
    private String line1;

    @Column(name = "DELIVERY_LINE2", length = 255)
    private String line2;

    @Column(name = "DELIVERY_CITY", length = 100)
    private String city;

    @Column(name = "DELIVERY_STATE", length = 100)
    private String state;

    @Column(name = "DELIVERY_PINCODE", length = 10)
    private String pincode;

    @Column(name = "DELIVERY_LANDMARK", length = 255)
    private String landmark;

    @Column(name = "DELIVERY_PHONE", length = 20)
    private String contactPhone;
}
