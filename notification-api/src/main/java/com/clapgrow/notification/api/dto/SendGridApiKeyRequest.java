package com.clapgrow.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendGridApiKeyRequest {
    @NotBlank(message = "SendGrid API key is required")
    @Size(min = 10, max = 255, message = "API key must be between 10 and 255 characters")
    private String sendgridApiKey;
    
    @Size(max = 255, message = "Email from address must not exceed 255 characters")
    private String emailFromAddress;
    
    @Size(max = 255, message = "Email from name must not exceed 255 characters")
    private String emailFromName;
}



