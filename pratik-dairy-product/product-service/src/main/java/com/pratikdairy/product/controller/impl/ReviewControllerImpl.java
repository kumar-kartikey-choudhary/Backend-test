package com.pratikdairy.product.controller.impl;

import com.pratikdairy.product.controller.ReviewController;
import com.pratikdairy.product.dto.ProductReviewsDto;
import com.pratikdairy.product.dto.RatingSummaryDto;
import com.pratikdairy.product.dto.ReviewDto;
import com.pratikdairy.product.dto.ReviewRequest;
import com.pratikdairy.product.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("products")
public class ReviewControllerImpl implements ReviewController {

    private final ReviewService reviewService;

    @Autowired
    public ReviewControllerImpl(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Override
    public ResponseEntity<ProductReviewsDto> reviews(String productId) {
        return ResponseEntity.ok(reviewService.reviews(productId));
    }

    @Override
    public ResponseEntity<List<RatingSummaryDto>> ratings() {
        return ResponseEntity.ok(reviewService.ratings());
    }

    @Override
    public ResponseEntity<ReviewDto> save(String productId, ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(reviewService.save(productId, request));
    }

    @Override
    public ResponseEntity<Void> delete(String reviewId) {
        reviewService.delete(reviewId);
        return ResponseEntity.ok().build();
    }
}
