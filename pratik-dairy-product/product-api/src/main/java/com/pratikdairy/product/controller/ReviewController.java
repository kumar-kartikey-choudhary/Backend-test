package com.pratikdairy.product.controller;

import com.pratikdairy.product.dto.ProductReviewsDto;
import com.pratikdairy.product.dto.RatingSummaryDto;
import com.pratikdairy.product.dto.ReviewDto;
import com.pratikdairy.product.dto.ReviewRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public interface ReviewController {

    /** Public. A product's reviews, newest first, with the average. */
    @GetMapping(path = "product/{productId}/reviews")
    ResponseEntity<ProductReviewsDto> reviews(@PathVariable("productId") String productId);

    /** Public. Average and count for every product that has reviews - for the stars on catalogue cards. */
    @GetMapping(path = "ratings")
    ResponseEntity<List<RatingSummaryDto>> ratings();

    /** Signed in. Adds the caller's review of a product, or replaces the one they wrote before. */
    @PostMapping(path = "product/{productId}/reviews")
    ResponseEntity<ReviewDto> save(@PathVariable("productId") String productId, @Valid @RequestBody ReviewRequest request);

    /** Signed in. Deletes a review: its author may, and so may an admin. */
    @DeleteMapping(path = "reviews/{reviewId}")
    ResponseEntity<Void> delete(@PathVariable("reviewId") String reviewId);
}
