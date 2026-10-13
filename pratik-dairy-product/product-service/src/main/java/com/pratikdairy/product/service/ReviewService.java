package com.pratikdairy.product.service;

import com.pratikdairy.product.dto.ProductReviewsDto;
import com.pratikdairy.product.dto.RatingSummaryDto;
import com.pratikdairy.product.dto.ReviewDto;
import com.pratikdairy.product.dto.ReviewRequest;

import java.util.List;

public interface ReviewService {
    ProductReviewsDto reviews(String productId);
    List<RatingSummaryDto> ratings();
    ReviewDto save(String productId, ReviewRequest request);
    void delete(String reviewId);
}
