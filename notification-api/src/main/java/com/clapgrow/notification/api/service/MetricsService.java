package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.dto.ChannelMetrics;
import com.clapgrow.notification.api.dto.DailyMetric;
import com.clapgrow.notification.api.dto.DailyMetricsResponse;
import com.clapgrow.notification.api.dto.MetricsSummaryResponse;
import com.clapgrow.notification.api.entity.SiteMetricsDaily;
import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import com.clapgrow.notification.api.repository.MessageLogRepository;
import com.clapgrow.notification.api.repository.SiteMetricsDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {
    
    private final MessageLogRepository messageLogRepository;
    private final SiteMetricsDailyRepository metricsDailyRepository;

    public MetricsSummaryResponse getSiteSummary(UUID siteId) {
        // Count all messages that have been sent (excluding PENDING and SCHEDULED)
        Long totalSent = messageLogRepository.countBySiteIdAndStatus(siteId, DeliveryStatus.SENT.name()) +
                        messageLogRepository.countBySiteIdAndStatus(siteId, DeliveryStatus.DELIVERED.name()) +
                        messageLogRepository.countBySiteIdAndStatus(siteId, DeliveryStatus.FAILED.name()) +
                        messageLogRepository.countBySiteIdAndStatus(siteId, DeliveryStatus.BOUNCED.name()) +
                        messageLogRepository.countBySiteIdAndStatus(siteId, DeliveryStatus.REJECTED.name());
        Long totalSuccess = messageLogRepository.countBySiteIdAndStatus(siteId, DeliveryStatus.DELIVERED.name());
        Long totalFailed = messageLogRepository.countBySiteIdAndStatus(siteId, DeliveryStatus.FAILED.name()) +
                          messageLogRepository.countBySiteIdAndStatus(siteId, DeliveryStatus.BOUNCED.name()) +
                          messageLogRepository.countBySiteIdAndStatus(siteId, DeliveryStatus.REJECTED.name());

        Map<String, ChannelMetrics> channelMetricsMap = new HashMap<>();
        
        for (NotificationChannel channel : NotificationChannel.values()) {
            Long channelSent = messageLogRepository.countBySiteIdAndChannel(siteId, channel.name());
            Long channelSuccess = messageLogRepository.countBySiteIdAndChannelAndStatus(
                siteId, channel.name(), DeliveryStatus.DELIVERED.name()
            );
            Long channelFailed = messageLogRepository.countBySiteIdAndChannelAndStatus(
                siteId, channel.name(), DeliveryStatus.FAILED.name()
            ) + messageLogRepository.countBySiteIdAndChannelAndStatus(
                siteId, channel.name(), DeliveryStatus.BOUNCED.name()
            ) + messageLogRepository.countBySiteIdAndChannelAndStatus(
                siteId, channel.name(), DeliveryStatus.REJECTED.name()
            );

            ChannelMetrics metrics = new ChannelMetrics();
            metrics.setChannel(channel.name());
            metrics.setTotalSent(channelSent);
            metrics.setTotalSuccess(channelSuccess);
            metrics.setTotalFailed(channelFailed);
            
            channelMetricsMap.put(channel.name(), metrics);
        }

        MetricsSummaryResponse response = new MetricsSummaryResponse();
        response.setSiteId(siteId.toString());
        response.setTotalSent(totalSent);
        response.setTotalSuccess(totalSuccess);
        response.setTotalFailed(totalFailed);
        response.setChannelMetrics(channelMetricsMap);

        return response;
    }

    public DailyMetricsResponse getDailyMetrics(UUID siteId, LocalDate startDate, LocalDate endDate) {
        List<SiteMetricsDaily> dailyMetrics = metricsDailyRepository.findBySiteIdAndDateRange(
            siteId, startDate, endDate
        );

        Map<LocalDate, Map<String, SiteMetricsDaily>> groupedByDate = dailyMetrics.stream()
            .collect(Collectors.groupingBy(
                SiteMetricsDaily::getMetricDate,
                Collectors.toMap(SiteMetricsDaily::getChannel, m -> m)
            ));

        List<DailyMetric> dailyMetricList = new ArrayList<>();
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Map<String, SiteMetricsDaily> dateMetrics = groupedByDate.getOrDefault(date, Collections.emptyMap());
            
            DailyMetric dailyMetric = new DailyMetric();
            dailyMetric.setDate(date.toString());
            
            Map<String, ChannelMetrics> channelMetricsMap = new HashMap<>();
            long totalSent = 0, totalSuccess = 0, totalFailed = 0;
            
            for (NotificationChannel channel : NotificationChannel.values()) {
                SiteMetricsDaily metrics = dateMetrics.get(channel.name());
                
                ChannelMetrics channelMetrics = new ChannelMetrics();
                channelMetrics.setChannel(channel.name());
                
                if (metrics != null) {
                    channelMetrics.setTotalSent(metrics.getTotalSent());
                    channelMetrics.setTotalSuccess(metrics.getTotalSuccess());
                    channelMetrics.setTotalFailed(metrics.getTotalFailed());
                    totalSent += metrics.getTotalSent();
                    totalSuccess += metrics.getTotalSuccess();
                    totalFailed += metrics.getTotalFailed();
                } else {
                    channelMetrics.setTotalSent(0L);
                    channelMetrics.setTotalSuccess(0L);
                    channelMetrics.setTotalFailed(0L);
                }
                
                channelMetricsMap.put(channel.name(), channelMetrics);
            }
            
            dailyMetric.setTotalSent(totalSent);
            dailyMetric.setTotalSuccess(totalSuccess);
            dailyMetric.setTotalFailed(totalFailed);
            dailyMetric.setChannelMetrics(channelMetricsMap);
            
            dailyMetricList.add(dailyMetric);
        }

        DailyMetricsResponse response = new DailyMetricsResponse();
        response.setSiteId(siteId.toString());
        response.setDailyMetrics(dailyMetricList);

        return response;
    }
}

