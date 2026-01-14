package com.clapgrow.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendGridApiKeyRequest {
    private String sendgridApiKey;
    private String emailFromAddress;
    private String emailFromName;
}

