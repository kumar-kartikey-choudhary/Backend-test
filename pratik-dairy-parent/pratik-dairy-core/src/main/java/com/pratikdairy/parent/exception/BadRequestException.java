package com.pratikdairy.parent.exception;

import org.springframework.http.HttpStatus;

/** The request is malformed or fails validation (400). */
public class BadRequestException extends BaseException {

    public BadRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }
}
