package com.pratikdairy.parent.exception;

import org.springframework.http.HttpStatus;

/** A coupon code is unknown, inactive, expired, used up, or does not apply to this order (400). */
public class InvalidCouponException extends BaseException {

    public InvalidCouponException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_COUPON");
    }
}
