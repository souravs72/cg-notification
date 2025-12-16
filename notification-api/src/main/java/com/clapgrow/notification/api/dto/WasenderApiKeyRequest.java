package com.clapgrow.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WasenderApiKeyRequest {
    @NotBlank(message = "WASender API key is required")
    @Size(min = 10, max = 255, message = "API key must be between 10 and 255 characters")
    private String wasenderApiKey;
}









