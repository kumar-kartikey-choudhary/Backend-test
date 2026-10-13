package com.pratikdairy.parent.exception;

import org.springframework.http.HttpStatus;

/** A downstream dependency (payment gateway, another microservice) failed or answered unusably (502). */
public class ExternalServiceException extends BaseException {

    public ExternalServiceException(String message) {
        super(message, HttpStatus.BAD_GATEWAY, "EXTERNAL_SERVICE_ERROR");
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_GATEWAY, "EXTERNAL_SERVICE_ERROR");
    }
}
