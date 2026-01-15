package com.clapgrow.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteRegistrationRequest {
    @NotBlank(message = "Site name is required")
    @Size(max = 255, message = "Site name must not exceed 255 characters")
    private String siteName;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 255, message = "WhatsApp session name must not exceed 255 characters")
    private String whatsappSessionName;

    @Size(max = 255, message = "Email from address must not exceed 255 characters")
    private String emailFromAddress;

    @Size(max = 255, message = "Email from name must not exceed 255 characters")
    private String emailFromName;
}

