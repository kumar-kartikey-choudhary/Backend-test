package com.pratikdairy.parent.exception;

import org.springframework.http.HttpStatus;

/** The caller is not authenticated, or the credentials / token presented are not valid (401). */
public class UnauthorizedException extends BaseException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }
}
