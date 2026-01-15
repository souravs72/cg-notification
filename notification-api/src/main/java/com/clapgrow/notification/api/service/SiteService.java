package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.dto.SiteRegistrationRequest;
import com.clapgrow.notification.api.dto.SiteRegistrationResponse;
import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.repository.FrappeSiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteService {
    
    private final FrappeSiteRepository siteRepository;
    private final ApiKeyService apiKeyService;

    @Transactional
    public SiteRegistrationResponse registerSite(SiteRegistrationRequest request) {
        if (siteRepository.existsBySiteNameAndIsDeletedFalse(request.getSiteName())) {
            throw new IllegalArgumentException("Site with name '" + request.getSiteName() + "' already exists");
        }

        FrappeSite site = siteRepository.findBySiteName(request.getSiteName())
            .filter(s -> Boolean.TRUE.equals(s.getIsDeleted()))
            .orElse(null);

        String apiKey;
        if (site != null) {
            apiKey = apiKeyService.generateApiKey();
            site.setApiKey(apiKey);
            site.setApiKeyHash(apiKeyService.hashApiKey(apiKey));
            site.setDescription(request.getDescription());
            site.setWhatsappSessionName(request.getWhatsappSessionName());
            site.setEmailFromAddress(request.getEmailFromAddress());
            site.setEmailFromName(request.getEmailFromName());
            site.setIsActive(true);
            site.setIsDeleted(false);
            site.setUpdatedBy("SYSTEM");
            log.info("Restored deleted site: {} with ID: {}", site.getSiteName(), site.getId());
        } else {
            apiKey = apiKeyService.generateApiKey();
            String apiKeyHash = apiKeyService.hashApiKey(apiKey);
            site = new FrappeSite();
            site.setSiteName(request.getSiteName());
            site.setApiKey(apiKey);
            site.setApiKeyHash(apiKeyHash);
            site.setDescription(request.getDescription());
            site.setWhatsappSessionName(request.getWhatsappSessionName());
            site.setEmailFromAddress(request.getEmailFromAddress());
            site.setEmailFromName(request.getEmailFromName());
            site.setIsActive(true);
            site.setCreatedBy("SYSTEM");
            log.info("Registered new site: {} with ID: {}", site.getSiteName(), site.getId());
        }

        site = siteRepository.save(site);

        return new SiteRegistrationResponse(
            site.getId(),
            site.getSiteName(),
            apiKey,
            "Site registered successfully. Please save your API key securely."
        );
    }
    

    public FrappeSite validateApiKey(String apiKey) {
        return siteRepository.findAll().stream()
            .filter(site -> apiKeyService.validateApiKey(apiKey, site.getApiKeyHash()))
            .filter(FrappeSite::getIsActive)
            .filter(site -> !Boolean.TRUE.equals(site.getIsDeleted()))
            .findFirst()
            .orElseThrow(() -> new SecurityException("Invalid or inactive API key"));
    }

    public FrappeSite getSiteById(UUID siteId) {
        return siteRepository.findById(siteId)
            .orElseThrow(() -> new IllegalArgumentException("Site not found: " + siteId));
    }

    public List<FrappeSite> getAllSites() {
        return siteRepository.findAll().stream()
            .filter(site -> !Boolean.TRUE.equals(site.getIsDeleted()))
            .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSite(UUID siteId) {
        FrappeSite site = getSiteById(siteId);
        site.setIsActive(false);
        site.setIsDeleted(true);
        site.setUpdatedBy("ADMIN");
        siteRepository.save(site);
        log.info("Deleted site: {} with ID: {}", site.getSiteName(), siteId);
    }

    @Transactional
    public FrappeSite updateSite(FrappeSite site) {
        return siteRepository.save(site);
    }

    @Transactional
    public String regenerateApiKey(UUID siteId) {
        FrappeSite site = getSiteById(siteId);
        String newApiKey = apiKeyService.generateApiKey();
        site.setApiKey(newApiKey);
        site.setApiKeyHash(apiKeyService.hashApiKey(newApiKey));
        site.setUpdatedBy("SYSTEM");
        siteRepository.save(site);
        log.info("Regenerated API key for site: {} with ID: {}", site.getSiteName(), siteId);
        return newApiKey;
    }
}

