package com.clapgrow.notification.api.service;

/**
 * Exception thrown when a bad request is made (e.g., invalid input, validation failure).
 * 
 * FUTURE: Use this instead of IllegalArgumentException for user-facing errors.
 * Allows controllers to map to HTTP 400 Bad Request.
 * 
 * Example usage:
 * <pre>
 * if (apiKey == null || apiKey.trim().isEmpty()) {
 *     throw new BadRequestException("API key is required");
 * }
 * </pre>
 */
public class BadRequestException extends RuntimeException {
    
    public BadRequestException(String message) {
        super(message);
    }
    
    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}





