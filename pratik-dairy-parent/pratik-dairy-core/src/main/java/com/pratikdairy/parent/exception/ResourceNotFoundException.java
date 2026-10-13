package com.pratikdairy.parent.exception;

import org.springframework.http.HttpStatus;

/** The requested resource does not exist (404). */
public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    /** "{@code Product not found with id: 42}" */
    public ResourceNotFoundException(String resource, Object id) {
        this(resource + " not found with id: " + id);
    }
}
