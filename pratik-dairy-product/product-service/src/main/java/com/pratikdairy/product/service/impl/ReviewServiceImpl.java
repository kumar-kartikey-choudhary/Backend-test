package com.pratikdairy.product.service.impl;

import com.pratikdairy.parent.exception.ResourceNotFoundException;
import com.pratikdairy.parent.exception.UnauthorizedException;
import com.pratikdairy.product.dto.ProductReviewsDto;
import com.pratikdairy.product.dto.RatingSummaryDto;
import com.pratikdairy.product.dto.ReviewDto;
import com.pratikdairy.product.dto.ReviewRequest;
import com.pratikdairy.product.model.ProductReview;
import com.pratikdairy.product.repository.ProductRepository;
import com.pratikdairy.product.repository.ProductReviewRepository;
import com.pratikdairy.product.service.ReviewService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ProductReviewRepository reviews;
    private final ProductRepository products;

    @Autowired
    public ReviewServiceImpl(ProductReviewRepository reviews, ProductRepository products) {
        this.reviews = reviews;
        this.products = products;
    }

    /** The signed-in username, or null for an anonymous visitor (reading reviews is public). */
    private static String usernameOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getName();
    }

    private static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** Reviews are public, so the author is shown only partly: "ravi" becomes "ra***". */
    static String mask(String username) {
        if (username == null || username.isBlank()) return "customer";
        String trimmed = username.trim();
        return trimmed.substring(0, Math.min(2, trimmed.length())) + "***";
    }

    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private ReviewDto toDto(ProductReview review, String viewer) {
        ReviewDto dto = new ReviewDto();
        dto.setId(review.getId());
        dto.setProductId(review.getProductId());
        dto.setAuthorName(mask(review.getUsername()));
        dto.setRating(review.getRating());
        dto.setComment(review.getComment());
        dto.setCreatedAt(review.getCreatedAt());
        dto.setMine(viewer != null && viewer.equals(review.getUsername()));
        return dto;
    }

    @Override
    public ProductReviewsDto reviews(String productId) {
        if (!products.existsById(productId)) {
            throw new ResourceNotFoundException("Product", productId);
        }
        String viewer = usernameOrNull();
        List<ProductReview> all = reviews.findByProductIdOrderByCreatedAtDesc(productId);
        double average = all.isEmpty() ? 0 : round1(all.stream().mapToInt(ProductReview::getRating).average().orElse(0));
        return new ProductReviewsDto(average, all.size(), all.stream().map(r -> toDto(r, viewer)).toList());
    }

    @Override
    public List<RatingSummaryDto> ratings() {
        return reviews.summarise().stream()
                .map(row -> new RatingSummaryDto((String) row[0], round1(((Number) row[1]).doubleValue()), ((Number) row[2]).intValue()))
                .toList();
    }

    @Override
    @Transactional
    public ReviewDto save(String productId, ReviewRequest request) {
        String username = usernameOrNull();
        if (username == null) {
            throw new UnauthorizedException("User not authenticated");
        }
        if (!products.existsById(productId)) {
            throw new ResourceNotFoundException("Product", productId);
        }
        ProductReview review = reviews.findByProductIdAndUsername(productId, username).orElseGet(() -> {
            ProductReview created = new ProductReview();
            created.setProductId(productId);
            created.setUsername(username);
            return created;
        });
        review.setRating(request.getRating());
        review.setComment(request.getComment() == null || request.getComment().isBlank() ? null : request.getComment().trim());
        return toDto(reviews.saveAndFlush(review), username);
    }

    @Override
    @Transactional
    public void delete(String reviewId) {
        String username = usernameOrNull();
        if (username == null) {
            throw new UnauthorizedException("User not authenticated");
        }
        ProductReview review = reviews.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        if (!review.getUsername().equals(username) && !isAdmin()) {
            throw new AccessDeniedException("This review does not belong to the current user");
        }
        reviews.delete(review);
    }
}
