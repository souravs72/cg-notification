package com.clapgrow.notification.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "site_metrics_daily", indexes = {
    @Index(name = "idx_site_date", columnList = "site_id, metric_date"),
    @Index(name = "idx_metric_date", columnList = "metric_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SiteMetricsDaily extends BaseAuditableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "total_sent", nullable = false)
    private Long totalSent = 0L;

    @Column(name = "total_success", nullable = false)
    private Long totalSuccess = 0L;

    @Column(name = "total_failed", nullable = false)
    private Long totalFailed = 0L;

    @Version
    @Column(name = "version")
    private Long version;
}

