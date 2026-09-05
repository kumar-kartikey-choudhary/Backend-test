package com.pratikdairy.product.dto;

import com.pratikdairy.parent.base.dto.BaseDto;
import com.pratikdairy.product.enums.InventoryReason;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = false)
public class InventoryLedgerDto extends BaseDto {

    private String productId;
    private BigDecimal changeQty;
    private InventoryReason reason;
    private String referenceId;
}