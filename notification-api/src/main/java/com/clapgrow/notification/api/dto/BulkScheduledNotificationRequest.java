package com.clapgrow.notification.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BulkScheduledNotificationRequest {
    @NotEmpty(message = "Notifications list cannot be empty")
    @NotNull(message = "Notifications list is required")
    @Valid
    private List<ScheduledNotificationRequest> notifications;
}

