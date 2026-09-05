package com.pratikdairy.order.dto;

import com.pratikdairy.order.enums.OrderStatus;
import com.pratikdairy.parent.base.dto.BaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderStatusHistoryDto extends BaseDto {
    private OrderStatus status;
    private String remarks;
    private String changedBy;
    // createdAt (inherited from BaseDto) is when this transition happened.
}