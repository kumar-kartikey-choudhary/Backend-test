package com.pratikdairy.parent.exception;

import org.springframework.http.HttpStatus;

/** The operation is not allowed in the resource's current state, e.g. refunding a payment that never succeeded (409). */
public class InvalidStateException extends BaseException {

    public InvalidStateException(String message) {
        super(message, HttpStatus.CONFLICT, "INVALID_STATE");
    }
}
