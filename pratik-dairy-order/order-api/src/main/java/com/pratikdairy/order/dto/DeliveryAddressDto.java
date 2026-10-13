package com.pratikdairy.order.dto;

import lombok.Data;

/** Delivery address sent with checkout and echoed back on the order. See {@code DeliveryAddress}. */
@Data
public class DeliveryAddressDto {
    private String label;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String pincode;
    private String landmark;
    private String contactPhone;
}
