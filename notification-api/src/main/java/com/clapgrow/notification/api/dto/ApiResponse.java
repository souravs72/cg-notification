package com.clapgrow.notification.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standardized API response wrapper.
 * 
 * Provides consistent response structure across all API endpoints,
 * improving OpenAPI documentation accuracy and client contract safety.
 * 
 * Example usage:
 * <pre>
 * {@code
 * return ResponseEntity.ok(ApiResponse.success(data));
 * return ResponseEntity.status(400).body(ApiResponse.error("Invalid input"));
 * }
 * </pre>
 * 
 * @param <T> Type of the data payload
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    T data,
    String error
) {
    /**
     * Create a successful response with data.
     * 
     * @param data Response data
     * @param <T> Data type
     * @return Success response
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }
    
    /**
     * Create a successful response without data.
     * 
     * @return Success response with Void data type
     */
    public static ApiResponse<Void> successEmpty() {
        return new ApiResponse<>(true, null, null);
    }
    
    /**
     * Create an error response.
     * 
     * @param error Error message
     * @param <T> Data type
     * @return Error response
     */
    public static <T> ApiResponse<T> error(String error) {
        return new ApiResponse<>(false, null, error);
    }
}

