package com.pratikdairy.payment.dto;

import com.pratikdairy.parent.base.dto.BaseDto;
import com.pratikdairy.payment.enums.CardNetwork;
import lombok.Data;
import lombok.EqualsAndHashCode;

// Deliberately does NOT include gatewayTokenId or gatewayCustomerId - the frontend only ever
// needs enough to render "Visa ending 4242, exp 08/27" and to pass this dto's `id` back when
// choosing to pay with it. The actual token stays server-side.
@Data
@EqualsAndHashCode(callSuper = true)
public class SavedPaymentMethodDto extends BaseDto {
    private String cardLastFour;
    private CardNetwork cardNetwork;
    private Integer cardExpiryMonth;
    private Integer cardExpiryYear;
    private boolean isDefault;
}