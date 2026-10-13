package com.pratikdairy.parent.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Root of every business exception in Pratik Dairy. Carries the HTTP status the failure maps to
 * and a stable, machine-readable {@code errorCode} so {@code GlobalExceptionHandler} can turn it
 * into a response without each service writing its own try/catch.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected BaseException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    protected BaseException(String message, Throwable cause, HttpStatus status, String errorCode) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}
