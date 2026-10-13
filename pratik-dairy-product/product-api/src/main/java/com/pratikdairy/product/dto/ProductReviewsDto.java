package com.pratikdairy.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewsDto {
    /** Average stars rounded to one decimal; 0 when there are no reviews. */
    private double average;
    private int count;
    private List<ReviewDto> reviews = new ArrayList<>();
}
