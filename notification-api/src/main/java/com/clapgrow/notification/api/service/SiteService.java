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
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteService {
    
    private final FrappeSiteRepository siteRepository;
    private final ApiKeyService apiKeyService;
    private final WasenderQRService wasenderQRService;

    @Transactional
    public SiteRegistrationResponse registerSite(SiteRegistrationRequest request) {
        if (siteRepository.existsBySiteName(request.getSiteName())) {
            throw new IllegalArgumentException("Site with name '" + request.getSiteName() + "' already exists");
        }

        String apiKey = apiKeyService.generateApiKey();
        String apiKeyHash = apiKeyService.hashApiKey(apiKey);
        
        String whatsappSessionName = generateWhatsAppSessionName(request.getSiteName());
        Map<String, Object> sessionResult = wasenderQRService.createSession(whatsappSessionName);
        
        if (!Boolean.TRUE.equals(sessionResult.get("success"))) {
            log.warn("Failed to create WhatsApp session for site {}, continuing without session", 
                request.getSiteName());
        }

        FrappeSite site = new FrappeSite();
        site.setSiteName(request.getSiteName());
        site.setApiKey(apiKey);
        site.setApiKeyHash(apiKeyHash);
        site.setDescription(request.getDescription());
        site.setWhatsappSessionName(whatsappSessionName);
        site.setIsActive(true);
        site.setCreatedBy("SYSTEM");

        site = siteRepository.save(site);

        log.info("Registered new site: {} with ID: {} and WhatsApp session: {}", 
            site.getSiteName(), site.getId(), whatsappSessionName);

        return new SiteRegistrationResponse(
            site.getId(),
            site.getSiteName(),
            apiKey,
            "Site registered successfully. Please save your API key securely."
        );
    }
    
    private String generateWhatsAppSessionName(String siteName) {
        return "site-" + siteName.toLowerCase()
            .replaceAll("[^a-z0-9]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }

    public FrappeSite validateApiKey(String apiKey) {
        return siteRepository.findAll().stream()
            .filter(site -> apiKeyService.validateApiKey(apiKey, site.getApiKeyHash()))
            .filter(FrappeSite::getIsActive)
            .findFirst()
            .orElseThrow(() -> new SecurityException("Invalid or inactive API key"));
    }

    public FrappeSite getSiteById(UUID siteId) {
        return siteRepository.findById(siteId)
            .orElseThrow(() -> new IllegalArgumentException("Site not found: " + siteId));
    }

    public List<FrappeSite> getAllSites() {
        return siteRepository.findAll();
    }
}

