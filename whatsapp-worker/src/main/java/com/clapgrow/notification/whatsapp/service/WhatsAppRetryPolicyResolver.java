package com.clapgrow.notification.whatsapp.service;

import com.clapgrow.notification.common.retry.FailureClassification;
import com.clapgrow.notification.common.retry.RetryPolicyResolver;
import org.springframework.stereotype.Service;

/**
 * WhatsApp-specific implementation of RetryPolicyResolver.
 * 
 * Maps failure classifications to retry policies for WhatsApp messages.
 * Uses the shared RetryPolicyResolver interface from common-proto.
 * 
 * Future: Can be customized for WhatsApp-specific retry strategies if needed.
 */
@Service
public class WhatsAppRetryPolicyResolver implements RetryPolicyResolver {
    
    @Override
    public RetryPolicy resolve(FailureClassification classification) {
        return switch (classification) {
            case PERMANENT -> RetryPolicy.noRetry();
            case RATE_LIMIT -> RetryPolicy.exponentialBackoff();
            case TRANSIENT -> RetryPolicy.standard();
        };
    }
}



