package com.pratikdairy.parent.exception;

import org.springframework.http.HttpStatus;

/** The caller has tried too often and must wait (429) - e.g. a locked-out sign-in. */
public class TooManyRequestsException extends BaseException {

    public TooManyRequestsException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
    }
}
