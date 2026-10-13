package com.pratikdairy.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatingSummaryDto {
    private String productId;
    private double average;
    private int count;
}
