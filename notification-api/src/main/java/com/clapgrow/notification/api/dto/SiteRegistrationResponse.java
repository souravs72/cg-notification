package com.clapgrow.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiteRegistrationResponse {
    private UUID siteId;
    private String siteName;
    private String apiKey;
    private String message;
}

