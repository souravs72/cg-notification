package com.clapgrow.notification.whatsapp.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of sending a message via WASender API.
 * Contains success status and detailed error information if the send failed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WasenderSendResult {
    private boolean success;
    private String errorMessage;
    private String errorDetails; // Full error details including HTTP status, response body, etc.
    private Integer httpStatusCode;
    private String responseBody;
    
    public static WasenderSendResult success() {
        return new WasenderSendResult(true, null, null, null, null);
    }
    
    public static WasenderSendResult failure(String errorMessage) {
        return new WasenderSendResult(false, errorMessage, errorMessage, null, null);
    }
    
    public static WasenderSendResult failure(String errorMessage, String errorDetails) {
        return new WasenderSendResult(false, errorMessage, errorDetails, null, null);
    }
    
    public static WasenderSendResult failure(String errorMessage, String errorDetails, Integer httpStatusCode, String responseBody) {
        return new WasenderSendResult(false, errorMessage, errorDetails, httpStatusCode, responseBody);
    }
}


