package com.clapgrow.notification.api.repository;

import com.clapgrow.notification.api.entity.SiteMetricsDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteMetricsDailyRepository extends JpaRepository<SiteMetricsDaily, UUID> {
    Optional<SiteMetricsDaily> findBySiteIdAndMetricDateAndChannel(
        UUID siteId, 
        LocalDate metricDate, 
        String channel
    );
    
    @Query("SELECT s FROM SiteMetricsDaily s WHERE s.siteId = :siteId AND s.metricDate BETWEEN :startDate AND :endDate")
    List<SiteMetricsDaily> findBySiteIdAndDateRange(
        @Param("siteId") UUID siteId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    @Query("SELECT s FROM SiteMetricsDaily s WHERE s.siteId = :siteId ORDER BY s.metricDate DESC")
    List<SiteMetricsDaily> findBySiteIdOrderByDateDesc(@Param("siteId") UUID siteId);
}

