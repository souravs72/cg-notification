package com.clapgrow.notification.api.repository;

import com.clapgrow.notification.api.entity.FrappeSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FrappeSiteRepository extends JpaRepository<FrappeSite, UUID> {
    Optional<FrappeSite> findBySiteName(String siteName);
    Optional<FrappeSite> findByApiKeyHash(String apiKeyHash);
    boolean existsBySiteName(String siteName);
    boolean existsBySiteNameAndIsDeletedFalse(String siteName);
}

