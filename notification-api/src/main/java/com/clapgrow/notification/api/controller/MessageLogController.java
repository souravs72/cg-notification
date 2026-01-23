package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.dto.MessageLogResponse;
import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.entity.MessageLog;
import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import com.clapgrow.notification.api.repository.FrappeSiteRepository;
import com.clapgrow.notification.api.repository.MessageLogRepository;
import com.clapgrow.notification.api.service.SiteService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Tag(name = "Message Logs", description = "API endpoints for retrieving message logs and statistics")
public class MessageLogController {
    
    private final MessageLogRepository messageLogRepository;
    private final FrappeSiteRepository siteRepository;
    private final SiteService siteService;
    private final ObjectMapper objectMapper;

    @GetMapping("/logs")
    @Operation(
            summary = "Get message logs",
            description = "Retrieves paginated message logs for the authenticated site. Supports filtering by status, channel, and date range."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message logs retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API key")
    })
    @SecurityRequirement(name = "SiteKey")
    public ResponseEntity<Map<String, Object>> getMessageLogs(
            @Parameter(description = "Site API key for authentication", required = true)
            @RequestHeader(name = "X-Site-Key") String apiKey,
            @Parameter(description = "Filter by delivery status (PENDING, SCHEDULED, SENT, DELIVERED, FAILED)")
            @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Filter by notification channel (EMAIL, WHATSAPP)")
            @RequestParam(name = "channel", required = false) String channel,
            @Parameter(description = "Start date for filtering (ISO format: YYYY-MM-DD)")
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date for filtering (ISO format: YYYY-MM-DD)")
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(name = "size", defaultValue = "20") int size) {
        
        DeliveryStatus statusEnum = null;
        NotificationChannel channelEnum = null;
        
        if (status != null && !status.isEmpty()) {
            try {
                statusEnum = DeliveryStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Invalid status: " + status + ". Valid values: " + 
                    java.util.Arrays.toString(DeliveryStatus.values()));
                return ResponseEntity.badRequest().body(error);
            }
        }
        
        if (channel != null && !channel.isEmpty()) {
            try {
                channelEnum = NotificationChannel.valueOf(channel.toUpperCase());
            } catch (IllegalArgumentException e) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Invalid channel: " + channel + ". Valid values: " + 
                    java.util.Arrays.toString(NotificationChannel.values()));
                return ResponseEntity.badRequest().body(error);
            }
        }
        
        FrappeSite site = siteService.validateApiKey(apiKey);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<MessageLog> messageLogs;
        
        if (statusEnum != null && channelEnum != null && startDate != null && endDate != null) {
            messageLogs = messageLogRepository.findBySiteIdAndStatusAndChannelAndCreatedAtBetween(
                site.getId(), statusEnum, channelEnum, 
                startDate.atStartOfDay(), 
                endDate.atTime(23, 59, 59), 
                pageable
            );
        } else if (statusEnum != null && channelEnum != null) {
            messageLogs = messageLogRepository.findBySiteIdAndStatusAndChannel(
                site.getId(), statusEnum, channelEnum, pageable
            );
        } else if (statusEnum != null) {
            messageLogs = messageLogRepository.findBySiteIdAndStatus(
                site.getId(), statusEnum, pageable
            );
        } else if (channelEnum != null) {
            messageLogs = messageLogRepository.findBySiteIdAndChannel(
                site.getId(), channelEnum, pageable
            );
        } else if (startDate != null && endDate != null) {
            messageLogs = messageLogRepository.findBySiteIdAndCreatedAtBetween(
                site.getId(),
                startDate.atStartOfDay(),
                endDate.atTime(23, 59, 59),
                pageable
            );
        } else {
            messageLogs = messageLogRepository.findBySiteId(site.getId(), pageable);
        }
        
        // Fix N+1 query: Fetch all sites in one query instead of per message
        List<UUID> siteIds = messageLogs.getContent().stream()
            .map(MessageLog::getSiteId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        
        Map<UUID, String> siteNameMap = new HashMap<>();
        if (!siteIds.isEmpty()) {
            siteRepository.findAllById(siteIds).forEach(s -> 
                siteNameMap.put(s.getId(), s.getSiteName())
            );
        }
        
        List<MessageLogResponse> responses = messageLogs.getContent().stream()
            .map(log -> convertToResponse(log, siteNameMap))
            .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("messages", responses);
        result.put("totalElements", messageLogs.getTotalElements());
        result.put("totalPages", messageLogs.getTotalPages());
        result.put("currentPage", messageLogs.getNumber());
        result.put("pageSize", messageLogs.getSize());
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/logs/{messageId}")
    @Operation(
            summary = "Get message log by ID",
            description = "Retrieves detailed information about a specific message by its ID."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message log retrieved successfully",
                    content = @Content(schema = @Schema(implementation = MessageLogResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API key"),
            @ApiResponse(responseCode = "403", description = "Unauthorized access to message"),
            @ApiResponse(responseCode = "404", description = "Message not found")
    })
    @SecurityRequirement(name = "SiteKey")
    public ResponseEntity<?> getMessageLog(
            @Parameter(description = "Site API key for authentication", required = true)
            @RequestHeader(name = "X-Site-Key") String apiKey,
            @Parameter(description = "Message ID", required = true)
            @PathVariable(name = "messageId") String messageId) {
        
        FrappeSite site = siteService.validateApiKey(apiKey);
        
        MessageLog messageLog = messageLogRepository.findByMessageId(messageId)
            .orElse(null);
        
        if (messageLog == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Message not found");
            return ResponseEntity.status(404).body(error);
        }
        
        if (!messageLog.getSiteId().equals(site.getId())) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Unauthorized access to message");
            return ResponseEntity.status(403).body(error);
        }
        
        // Fetch site name for single message (no N+1 issue here)
        Map<UUID, String> siteNameMap = new HashMap<>();
        siteRepository.findById(messageLog.getSiteId()).ifPresent(s -> 
            siteNameMap.put(s.getId(), s.getSiteName())
        );
        
        return ResponseEntity.ok(convertToResponse(messageLog, siteNameMap));
    }

    @GetMapping("/stats")
    @Operation(
            summary = "Get message statistics",
            description = "Retrieves aggregated statistics for messages sent by the authenticated site including totals, success rates, and averages."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API key")
    })
    @SecurityRequirement(name = "SiteKey")
    public ResponseEntity<Map<String, Object>> getMessageStats(
            @Parameter(description = "Site API key for authentication", required = true)
            @RequestHeader(name = "X-Site-Key") String apiKey) {
        
        FrappeSite site = siteService.validateApiKey(apiKey);
        
        // Use enum-based methods for consistency (preferred API)
        long totalMessages = messageLogRepository.countBySiteId(site.getId());
        long pendingMessages = messageLogRepository.countBySiteIdAndStatus(site.getId(), DeliveryStatus.PENDING);
        long scheduledMessages = messageLogRepository.countBySiteIdAndStatus(site.getId(), DeliveryStatus.SCHEDULED);
        long sentMessages = messageLogRepository.countBySiteIdAndStatus(site.getId(), DeliveryStatus.SENT);
        long deliveredMessages = messageLogRepository.countBySiteIdAndStatus(site.getId(), DeliveryStatus.DELIVERED);
        long failedMessages = messageLogRepository.countBySiteIdAndStatus(site.getId(), DeliveryStatus.FAILED);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMessages", totalMessages);
        stats.put("pending", pendingMessages);
        stats.put("scheduled", scheduledMessages);
        stats.put("sent", sentMessages);
        stats.put("delivered", deliveredMessages);
        stats.put("failed", failedMessages);
        stats.put("successRate", totalMessages > 0 ? 
            (double) deliveredMessages * 100.0 / totalMessages : 0.0);
        stats.put("averageMessagesPerDay", calculateAverageMessagesPerDay(site.getId()));
        
        return ResponseEntity.ok(stats);
    }

    private MessageLogResponse convertToResponse(MessageLog messageLog, Map<UUID, String> siteNameMap) {
        MessageLogResponse response = new MessageLogResponse();
        response.setId(messageLog.getId());
        response.setMessageId(messageLog.getMessageId());
        response.setSiteId(messageLog.getSiteId());
        response.setChannel(messageLog.getChannel());
        response.setStatus(messageLog.getStatus());
        response.setRecipient(messageLog.getRecipient());
        response.setSubject(messageLog.getSubject());
        response.setBody(messageLog.getBody());
        response.setErrorMessage(messageLog.getErrorMessage());
        response.setRetryCount(messageLog.getRetryCount());
        response.setSentAt(messageLog.getSentAt());
        response.setDeliveredAt(messageLog.getDeliveredAt());
        response.setScheduledAt(messageLog.getScheduledAt());
        response.setCreatedAt(messageLog.getCreatedAt());
        response.setUpdatedAt(messageLog.getUpdatedAt());
        
        // WhatsApp fields
        response.setImageUrl(messageLog.getImageUrl());
        response.setVideoUrl(messageLog.getVideoUrl());
        response.setDocumentUrl(messageLog.getDocumentUrl());
        response.setFileName(messageLog.getFileName());
        response.setCaption(messageLog.getCaption());
        
        // Email fields
        response.setFromEmail(messageLog.getFromEmail());
        response.setFromName(messageLog.getFromName());
        response.setIsHtml(messageLog.getIsHtml());
        
        // Site name - use pre-fetched map to avoid N+1 queries
        if (messageLog.getSiteId() != null && siteNameMap.containsKey(messageLog.getSiteId())) {
            response.setSiteName(siteNameMap.get(messageLog.getSiteId()));
        }
        
        // Metadata
        if (messageLog.getMetadata() != null) {
            try {
                Map<String, String> metadata = objectMapper.readValue(
                    messageLog.getMetadata(), 
                    new TypeReference<Map<String, String>>() {}
                );
                response.setMetadata(metadata);
            } catch (Exception e) {
                // Ignore metadata parsing errors
            }
        }
        
        return response;
    }

    /**
     * Calculate average messages per day for a site.
     * Optimized to avoid loading all messages into memory (unbounded scan).
     * Uses efficient queries: COUNT and MIN(created_at) instead of loading all records.
     */
    private double calculateAverageMessagesPerDay(UUID siteId) {
        long totalMessages = messageLogRepository.countBySiteId(siteId);
        if (totalMessages == 0) {
            return 0.0;
        }
        
        // Get oldest message date efficiently (single query, no data loading)
        LocalDateTime oldestMessage = messageLogRepository.findOldestMessageDateBySiteId(siteId)
            .orElse(LocalDateTime.now());
        
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(oldestMessage, LocalDateTime.now());
        if (daysBetween == 0) {
            daysBetween = 1; // Avoid division by zero
        }
        
        return (double) totalMessages / daysBetween;
    }
}

