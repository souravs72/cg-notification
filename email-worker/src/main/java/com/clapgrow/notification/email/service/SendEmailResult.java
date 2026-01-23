package com.clapgrow.notification.email.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SendEmailResult {
    private boolean success;
    private String errorMessage;
    
    public static SendEmailResult success() {
        return new SendEmailResult(true, null);
    }
    
    public static SendEmailResult failure(String errorMessage) {
        return new SendEmailResult(false, errorMessage);
    }
}



