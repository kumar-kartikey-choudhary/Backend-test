package com.pratikdairy.product.dto;

import com.pratikdairy.parent.base.dto.BaseDto;
import com.pratikdairy.product.enums.Category;
import com.pratikdairy.product.enums.SweetType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class ProductDto extends BaseDto {

    private String productName;
    private BigDecimal price;
    private boolean available;

    // Stock is intentionally not free-form editable on update - it only moves through the
    // decrement/restore/adjust-stock endpoints so every change lands in the inventory ledger.
    // Still present here for create (starting stock) and for reading the current value.
    private BigDecimal stockQuantity;
    private String stockUnit;

    private Category category;
    private SweetType type;
    private String description;
    private LocalDate manufactureDate;
    private LocalDate expiryDate;
    private String status;

    private List<ProductImageDto> images = new ArrayList<>();
}