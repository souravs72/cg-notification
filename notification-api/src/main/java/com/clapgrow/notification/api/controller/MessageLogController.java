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
                throw new RuntimeException("Invalid status: " + status);
            }
        }
        
        if (channel != null && !channel.isEmpty()) {
            try {
                channelEnum = NotificationChannel.valueOf(channel.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid channel: " + channel);
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
        
        List<MessageLogResponse> responses = messageLogs.getContent().stream()
            .map(this::convertToResponse)
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
            @ApiResponse(responseCode = "404", description = "Message not found")
    })
    @SecurityRequirement(name = "SiteKey")
    public ResponseEntity<MessageLogResponse> getMessageLog(
            @Parameter(description = "Site API key for authentication", required = true)
            @RequestHeader(name = "X-Site-Key") String apiKey,
            @Parameter(description = "Message ID", required = true)
            @PathVariable(name = "messageId") String messageId) {
        
        FrappeSite site = siteService.validateApiKey(apiKey);
        
        MessageLog messageLog = messageLogRepository.findByMessageId(messageId)
            .orElseThrow(() -> new RuntimeException("Message not found"));
        
        if (!messageLog.getSiteId().equals(site.getId())) {
            throw new RuntimeException("Unauthorized access to message");
        }
        
        return ResponseEntity.ok(convertToResponse(messageLog));
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
        
        long totalMessages = messageLogRepository.countBySiteId(site.getId());
        long pendingMessages = messageLogRepository.countBySiteIdAndStatus(site.getId(), DeliveryStatus.PENDING.name());
        long scheduledMessages = messageLogRepository.countBySiteIdAndStatus(site.getId(), DeliveryStatus.SCHEDULED.name());
        long sentMessages = messageLogRepository.countBySiteIdAndStatus(site.getId(), DeliveryStatus.SENT.name());
        long deliveredMessages = messageLogRepository.countBySiteIdAndStatus(site.getId(), DeliveryStatus.DELIVERED.name());
        long failedMessages = messageLogRepository.countBySiteIdAndStatus(site.getId(), DeliveryStatus.FAILED.name());
        
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

    private MessageLogResponse convertToResponse(MessageLog messageLog) {
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
        
        // Site name
        siteRepository.findById(messageLog.getSiteId()).ifPresent(site -> {
            response.setSiteName(site.getSiteName());
        });
        
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

    private double calculateAverageMessagesPerDay(UUID siteId) {
        List<MessageLog> allMessages = messageLogRepository.findBySiteId(siteId);
        if (allMessages.isEmpty()) {
            return 0.0;
        }
        
        LocalDateTime oldestMessage = allMessages.stream()
            .map(MessageLog::getCreatedAt)
            .min(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now());
        
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(oldestMessage, LocalDateTime.now());
        if (daysBetween == 0) {
            daysBetween = 1;
        }
        
        return (double) allMessages.size() / daysBetween;
    }
}

