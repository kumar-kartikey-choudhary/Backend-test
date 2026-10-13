package com.pratikdairy.parent.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The JSON body every service returns on failure. {@code message} is what the Angular
 * http-error interceptor and the login/signup pages already read ({@code error.error.message}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
