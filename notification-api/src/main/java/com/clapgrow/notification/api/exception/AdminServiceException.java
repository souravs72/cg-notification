package com.clapgrow.notification.api.exception;

/**
 * Exception thrown by AdminService when admin operations fail.
 * This exception is handled by GlobalExceptionHandler to provide proper HTTP semantics.
 */
public class AdminServiceException extends RuntimeException {
    
    public AdminServiceException(String message) {
        super(message);
    }
    
    public AdminServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

