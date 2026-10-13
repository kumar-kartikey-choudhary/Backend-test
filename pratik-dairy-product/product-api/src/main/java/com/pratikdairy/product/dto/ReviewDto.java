package com.pratikdairy.product.dto;

import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class ReviewDto {
    private String id;
    private String productId;
    /** The reviewer's username, partly hidden (e.g. "ra***") - reviews are public. */
    private String authorName;
    private int rating;
    private String comment;
    private ZonedDateTime createdAt;
    /** True for the signed-in customer's own review, so the page can offer edit / delete. */
    private boolean mine;
}
