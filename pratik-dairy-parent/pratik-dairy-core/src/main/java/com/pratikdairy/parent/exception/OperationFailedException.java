package com.pratikdairy.parent.exception;

import org.springframework.http.HttpStatus;

/** An internal operation failed for a reason that is not the caller's fault, e.g. an I/O error (500). */
public class OperationFailedException extends BaseException {

    public OperationFailedException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR, "OPERATION_FAILED");
    }
}
