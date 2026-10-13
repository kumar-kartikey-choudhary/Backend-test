package com.pratikdairy.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewRequest {

    @NotNull(message = "Choose a rating from 1 to 5 stars.")
    @Min(value = 1, message = "The rating must be at least 1 star.")
    @Max(value = 5, message = "The rating can be at most 5 stars.")
    private Integer rating;

    @Size(max = 1000, message = "Your review can be at most 1000 characters.")
    private String comment;
}
