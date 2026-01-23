package com.clapgrow.notification.whatsapp.service;

import com.clapgrow.notification.common.retry.FailureClassification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Classifies message delivery failures to determine retry strategy.
 * 
 * Extracts failure classification logic from consumer processing,
 * making it reusable and testable.
 * 
 * Classification rules:
 * - PERMANENT: Authentication errors (401), invalid API keys
 * - RATE_LIMIT: HTTP 429 Too Many Requests
 * - TRANSIENT: All other failures (network errors, 5xx, etc.)
 * 
 * Provider-specific hints can be passed to customize classification
 * for different notification providers (WhatsApp, Email, SMS, etc.).
 */
@Service
@Slf4j
public class FailureClassifier {
    
    /**
     * Classify a failure based on HTTP status code, error message, and response body.
     * 
     * @param httpStatusCode HTTP status code (may be null)
     * @param errorMessage Error message (may be null)
     * @param responseBody Response body (may be null)
     * @return Failure classification
     */
    public FailureClassification classify(Integer httpStatusCode, String errorMessage, String responseBody) {
        return classify(httpStatusCode, errorMessage, responseBody, null);
    }
    
    /**
     * Classify a failure with provider-specific hints.
     * 
     * Provider-specific customization allows different providers to have
     * different classification rules while sharing core logic.
     * 
     * Example: WhatsApp might have different rate limit patterns than Email.
     * 
     * @param httpStatusCode HTTP status code (may be null)
     * @param errorMessage Error message (may be null)
     * @param responseBody Response body (may be null)
     * @param provider Provider name (e.g., "WASENDER", "SENDGRID") - may be null for generic classification
     * @return Failure classification
     */
    public FailureClassification classify(Integer httpStatusCode, String errorMessage, String responseBody, String provider) {
        // Rate limit detection (highest priority)
        if (httpStatusCode != null && httpStatusCode == 429) {
            return FailureClassification.RATE_LIMIT;
        }
        
        // Permanent failure detection: Authentication errors
        if (httpStatusCode != null && httpStatusCode == 401) {
            return FailureClassification.PERMANENT;
        }
        
        // Check error message for authentication-related keywords
        if (errorMessage != null) {
            String lowerError = errorMessage.toLowerCase();
            if (lowerError.contains("invalid api key") || 
                lowerError.contains("unauthorized") ||
                lowerError.contains("authentication") ||
                lowerError.contains("401")) {
                return FailureClassification.PERMANENT;
            }
        }
        
        // Check response body for authentication-related keywords
        if (responseBody != null) {
            String lowerResponse = responseBody.toLowerCase();
            if (lowerResponse.contains("invalid api key") || 
                (lowerResponse.contains("invalid") && lowerResponse.contains("key"))) {
                return FailureClassification.PERMANENT;
            }
        }
        
        // Provider-specific classification can be added here
        // Example: if ("WASENDER".equals(provider)) { ... }
        
        // Default to transient failure (retryable)
        return FailureClassification.TRANSIENT;
    }
}

