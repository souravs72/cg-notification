package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.dto.AdminDashboardResponse;
import com.clapgrow.notification.api.dto.AdminSiteMetrics;
import com.clapgrow.notification.api.dto.ChannelMetrics;
import com.clapgrow.notification.api.dto.MessageDetailResponse;
import com.clapgrow.notification.api.dto.MetricsSummaryResponse;
import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.entity.MessageLog;
import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import com.clapgrow.notification.api.repository.MessageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {
    
    private final MessageLogRepository messageLogRepository;
    private final MetricsService metricsService;
    private final SiteService siteService;

    public AdminDashboardResponse getDashboardMetrics() {
        List<FrappeSite> allSites = siteService.getAllSites();
        
        // Get metrics for all sites
        List<AdminSiteMetrics> siteMetricsList = allSites.stream()
            .map(site -> {
                MetricsSummaryResponse summary = metricsService.getSiteSummary(site.getId());
                
                double successRate = summary.getTotalSent() > 0 
                    ? (double) summary.getTotalSuccess() * 100.0 / summary.getTotalSent()
                    : 0.0;
                
                AdminSiteMetrics adminMetrics = new AdminSiteMetrics();
                adminMetrics.setSiteId(site.getId());
                adminMetrics.setSiteName(site.getSiteName());
                adminMetrics.setTotalSent(summary.getTotalSent());
                adminMetrics.setTotalSuccess(summary.getTotalSuccess());
                adminMetrics.setTotalFailed(summary.getTotalFailed());
                adminMetrics.setSuccessRate(Math.round(successRate * 100.0) / 100.0);
                adminMetrics.setChannelMetrics(summary.getChannelMetrics());
                
                return adminMetrics;
            })
            .collect(Collectors.toList());
        
        // Get metrics for messages without sites (siteId is null)
        long noSiteTotalSent = messageLogRepository.countByNullSiteIdAndStatus(DeliveryStatus.SENT.name()) +
                              messageLogRepository.countByNullSiteIdAndStatus(DeliveryStatus.DELIVERED.name()) +
                              messageLogRepository.countByNullSiteIdAndStatus(DeliveryStatus.FAILED.name()) +
                              messageLogRepository.countByNullSiteIdAndStatus(DeliveryStatus.BOUNCED.name()) +
                              messageLogRepository.countByNullSiteIdAndStatus(DeliveryStatus.REJECTED.name());
        
        long noSiteTotalSuccess = messageLogRepository.countByNullSiteIdAndStatus(DeliveryStatus.DELIVERED.name());
        
        long noSiteTotalFailed = messageLogRepository.countByNullSiteIdAndStatus(DeliveryStatus.FAILED.name()) +
                                messageLogRepository.countByNullSiteIdAndStatus(DeliveryStatus.BOUNCED.name()) +
                                messageLogRepository.countByNullSiteIdAndStatus(DeliveryStatus.REJECTED.name());
        
        // Add "No Site" entry if there are messages without sites
        if (noSiteTotalSent > 0) {
            Map<String, ChannelMetrics> noSiteChannelMetrics = new HashMap<>();
            for (NotificationChannel channel : NotificationChannel.values()) {
                Long channelSent = messageLogRepository.countByNullSiteIdAndChannel(channel.name());
                Long channelSuccess = messageLogRepository.countByNullSiteIdAndChannelAndStatus(
                    channel.name(), DeliveryStatus.DELIVERED.name()
                );
                Long channelFailed = messageLogRepository.countByNullSiteIdAndChannelAndStatus(
                    channel.name(), DeliveryStatus.FAILED.name()
                ) + messageLogRepository.countByNullSiteIdAndChannelAndStatus(
                    channel.name(), DeliveryStatus.BOUNCED.name()
                ) + messageLogRepository.countByNullSiteIdAndChannelAndStatus(
                    channel.name(), DeliveryStatus.REJECTED.name()
                );
                
                ChannelMetrics metrics = new ChannelMetrics();
                metrics.setChannel(channel.name());
                metrics.setTotalSent(channelSent);
                metrics.setTotalSuccess(channelSuccess);
                metrics.setTotalFailed(channelFailed);
                
                noSiteChannelMetrics.put(channel.name(), metrics);
            }
            
            AdminSiteMetrics noSiteMetrics = new AdminSiteMetrics();
            noSiteMetrics.setSiteId(null);
            noSiteMetrics.setSiteName("No Site");
            noSiteMetrics.setTotalSent(noSiteTotalSent);
            noSiteMetrics.setTotalSuccess(noSiteTotalSuccess);
            noSiteMetrics.setTotalFailed(noSiteTotalFailed);
            double noSiteSuccessRate = noSiteTotalSent > 0 
                ? Math.round((double) noSiteTotalSuccess * 10000.0 / noSiteTotalSent) / 100.0
                : 0.0;
            noSiteMetrics.setSuccessRate(noSiteSuccessRate);
            noSiteMetrics.setChannelMetrics(noSiteChannelMetrics);
            
            siteMetricsList.add(noSiteMetrics);
        }
        
        // Calculate totals including messages without sites
        long totalSent = siteMetricsList.stream()
            .mapToLong(AdminSiteMetrics::getTotalSent)
            .sum();
        
        long totalSuccess = siteMetricsList.stream()
            .mapToLong(AdminSiteMetrics::getTotalSuccess)
            .sum();
        
        long totalFailed = siteMetricsList.stream()
            .mapToLong(AdminSiteMetrics::getTotalFailed)
            .sum();
        
        double overallSuccessRate = totalSent > 0 
            ? Math.round((double) totalSuccess * 10000.0 / totalSent) / 100.0
            : 0.0;
        
        AdminDashboardResponse response = new AdminDashboardResponse();
        response.setTotalSites((long) allSites.size());
        response.setTotalMessagesSent(totalSent);
        response.setTotalMessagesSuccess(totalSuccess);
        response.setTotalMessagesFailed(totalFailed);
        response.setOverallSuccessRate(overallSuccessRate);
        response.setSiteMetrics(siteMetricsList);
        
        return response;
    }

    public List<MessageDetailResponse> getRecentMessages(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<MessageLog> messages = messageLogRepository.findAll(pageable).getContent();
        
        Map<java.util.UUID, String> siteNameMap = siteService.getAllSites().stream()
            .collect(Collectors.toMap(FrappeSite::getId, FrappeSite::getSiteName));
        
        return messages.stream()
            .map(msg -> {
                MessageDetailResponse detail = new MessageDetailResponse();
                detail.setMessageId(msg.getMessageId());
                detail.setSiteName(msg.getSiteId() != null 
                    ? siteNameMap.getOrDefault(msg.getSiteId(), "Unknown") 
                    : "No Site");
                detail.setChannel(msg.getChannel());
                detail.setStatus(msg.getStatus());
                detail.setRecipient(msg.getRecipient());
                detail.setSubject(msg.getSubject());
                detail.setBody(msg.getBody());
                detail.setErrorMessage(msg.getErrorMessage());
                detail.setSentAt(msg.getSentAt());
                detail.setDeliveredAt(msg.getDeliveredAt());
                detail.setScheduledAt(msg.getScheduledAt());
                detail.setCreatedAt(msg.getCreatedAt());
                return detail;
            })
            .collect(Collectors.toList());
    }

    public List<MessageDetailResponse> getFailedMessages(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<MessageLog> messages = messageLogRepository.findAll(pageable).getContent().stream()
            .filter(msg -> msg.getStatus() == DeliveryStatus.FAILED || 
                          msg.getStatus() == DeliveryStatus.BOUNCED || 
                          msg.getStatus() == DeliveryStatus.REJECTED)
            .limit(limit)
            .collect(Collectors.toList());
        
        Map<java.util.UUID, String> siteNameMap = siteService.getAllSites().stream()
            .collect(Collectors.toMap(FrappeSite::getId, FrappeSite::getSiteName));
        
        return messages.stream()
            .map(msg -> {
                MessageDetailResponse detail = new MessageDetailResponse();
                detail.setMessageId(msg.getMessageId());
                detail.setSiteName(msg.getSiteId() != null 
                    ? siteNameMap.getOrDefault(msg.getSiteId(), "Unknown") 
                    : "No Site");
                detail.setChannel(msg.getChannel());
                detail.setStatus(msg.getStatus());
                detail.setRecipient(msg.getRecipient());
                detail.setSubject(msg.getSubject());
                detail.setBody(msg.getBody());
                detail.setErrorMessage(msg.getErrorMessage());
                detail.setSentAt(msg.getSentAt());
                detail.setDeliveredAt(msg.getDeliveredAt());
                detail.setScheduledAt(msg.getScheduledAt());
                detail.setCreatedAt(msg.getCreatedAt());
                return detail;
            })
            .collect(Collectors.toList());
    }

    public List<MessageDetailResponse> getScheduledMessages(int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "scheduledAt"));
            Page<MessageLog> messagePage = messageLogRepository.findByStatusOrderByScheduledAtAsc(
                DeliveryStatus.SCHEDULED.name(), pageable
            );
            List<MessageLog> messages = messagePage.getContent();
            
            Map<java.util.UUID, String> siteNameMap = siteService.getAllSites().stream()
                .collect(Collectors.toMap(FrappeSite::getId, FrappeSite::getSiteName));
            
            return messages.stream()
                .map(msg -> {
                    MessageDetailResponse detail = new MessageDetailResponse();
                    detail.setMessageId(msg.getMessageId());
                    detail.setSiteName(msg.getSiteId() != null 
                        ? siteNameMap.getOrDefault(msg.getSiteId(), "Unknown") 
                        : "No Site");
                    detail.setChannel(msg.getChannel());
                    detail.setStatus(msg.getStatus());
                    detail.setRecipient(msg.getRecipient());
                    detail.setSubject(msg.getSubject());
                    detail.setBody(msg.getBody());
                    detail.setErrorMessage(msg.getErrorMessage());
                    detail.setSentAt(msg.getSentAt());
                    detail.setDeliveredAt(msg.getDeliveredAt());
                    detail.setScheduledAt(msg.getScheduledAt());
                    detail.setCreatedAt(msg.getCreatedAt());
                    return detail;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error in getScheduledMessages with limit: {}", limit, e);
            throw new RuntimeException("Failed to retrieve scheduled messages: " + e.getMessage(), e);
        }
    }
}

