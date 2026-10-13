package com.pratikdairy.parent.exception;

import org.springframework.http.HttpStatus;

/** A resource with the same unique value (username, email, coupon code, ...) already exists (409). */
public class DuplicateResourceException extends BaseException {

    public DuplicateResourceException(String message) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }
}
