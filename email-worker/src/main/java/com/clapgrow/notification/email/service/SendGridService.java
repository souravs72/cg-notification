package com.clapgrow.notification.email.service;

import com.clapgrow.notification.common.provider.EmailProvider;
import com.clapgrow.notification.common.provider.EmailResult;
import com.clapgrow.notification.common.provider.ProviderErrorCategory;
import com.clapgrow.notification.common.provider.ProviderName;
import com.clapgrow.notification.email.entity.FrappeSite;
import com.clapgrow.notification.email.entity.SendGridConfig;
import com.clapgrow.notification.email.model.NotificationPayload;
import com.clapgrow.notification.email.repository.FrappeSiteRepository;
import com.clapgrow.notification.email.repository.SendGridConfigRepository;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendGridService implements EmailProvider<NotificationPayload> {
    
    private final SendGridConfigRepository sendGridConfigRepository;
    private final FrappeSiteRepository frappeSiteRepository;
    
    @Value("${sendgrid.api.key:}")
    private String fallbackSendGridApiKey;
    
    @Value("${sendgrid.from.email:noreply@example.com}")
    private String defaultFromEmail;
    
    @Value("${sendgrid.from.name:Notification Service}")
    private String defaultFromName;

    @Override
    public ProviderName getProviderName() {
        return ProviderName.SENDGRID;
    }

    @Override
    public EmailResult sendEmail(NotificationPayload payload) {
        try {
            // Get SendGrid instance with API key (priority: payload > database > config file)
            SendGrid sendGrid = getSendGridForPayload(payload);
            
            if (sendGrid == null) {
                return EmailResult.createFailure(
                    "SendGrid API key is not configured. Please configure it in the admin panel.",
                    ProviderErrorCategory.CONFIG
                );
            }
            
            // Get from email/name (priority: payload > database config > default)
            String fromEmail = getFromEmail(payload);
            String fromName = getFromName(payload);
            
            Email from = new Email(fromEmail, fromName);
            Email to = new Email(payload.getRecipient());
            String subject = payload.getSubject() != null ? payload.getSubject() : "Notification";
            Content content = new Content(
                Boolean.TRUE.equals(payload.getIsHtml()) ? "text/html" : "text/plain",
                payload.getBody()
            );
            
            Mail mail = new Mail(from, subject, to, content);
            
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            
            Response response = sendGrid.api(request);
            
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("Email sent successfully to {} with status code {}", 
                    payload.getRecipient(), response.getStatusCode());
                return EmailResult.createSuccess();
            } else {
                // Categorize error based on HTTP status code
                ProviderErrorCategory errorCategory = categorizeError(response.getStatusCode());
                // SECURITY: Do not log or store raw response body - may contain secrets
                String bodyHint = response.getBody() != null && !response.getBody().isEmpty()
                    ? String.format("body length=%d", response.getBody().length())
                    : "no body";
                String errorMessage = String.format("SendGrid API error: Status %d (%s)", 
                    response.getStatusCode(), bodyHint);
                log.error("Failed to send email to {}: {}", 
                    payload.getRecipient(), errorMessage);
                return EmailResult.createFailure(errorMessage, errorCategory);
            }
            
        } catch (IOException e) {
            String errorMessage = String.format("SendGrid API IOException: %s", e.getMessage());
            log.error("Error sending email via SendGrid to {}", payload.getRecipient(), e);
            return EmailResult.createFailure(errorMessage, ProviderErrorCategory.TEMPORARY);
        } catch (Exception e) {
            String errorMessage = String.format("Unexpected error sending email: %s", e.getMessage());
            log.error("Unexpected error sending email via SendGrid to {}", payload.getRecipient(), e);
            return EmailResult.createFailure(errorMessage, ProviderErrorCategory.TEMPORARY);
        }
    }
    
    /**
     * Categorize SendGrid API error based on HTTP status code.
     * 
     * @param statusCode HTTP status code
     * @return ProviderErrorCategory
     */
    private ProviderErrorCategory categorizeError(int statusCode) {
        // 401 Unauthorized, 403 Forbidden = AUTH errors
        if (statusCode == 401 || statusCode == 403) {
            return ProviderErrorCategory.AUTH;
        } 
        // 429 Too Many Requests or 5xx Server Errors = TEMPORARY
        else if (statusCode == 429 || statusCode >= 500) {
            return ProviderErrorCategory.TEMPORARY;
        } 
        // 4xx Client Errors (except 401/403) = PERMANENT
        else if (statusCode >= 400 && statusCode < 500) {
            return ProviderErrorCategory.PERMANENT;
        } 
        // Unknown errors default to TEMPORARY
        else {
            return ProviderErrorCategory.TEMPORARY;
        }
    }
    
    /**
     * Resolve SendGrid API key from database.
     * 
     * Resolution priority:
     * 1. If siteId is present:
     *    - Lookup FrappeSite by siteId
     *    - Use site's sendgridApiKey if present
     * 2. Global SendGridConfig (sendgrid_config table)
     * 3. Environment variable fallback
     * 
     * ⚠️ SECURITY: API keys are resolved from database, never from payload or environment variables.
     * This ensures credentials are not exposed in Kafka topics, logs, or DLQs.
     * 
     * ⚠️ TENANT ISOLATION: API key resolution is tenant-scoped via siteId.
     * If siteId is null, global SendGridConfig is used (for messages without site association).
     * 
     * @param payload Notification payload containing siteId (may be null for global config)
     * @return SendGrid instance with API key, or null if not found
     */
    private SendGrid getSendGridForPayload(NotificationPayload payload) {
        // Priority 1: Resolve API key from site (frappe_sites table)
        if (payload.getSiteId() != null) {
            try {
                UUID siteId = payload.getSiteId();
                Optional<FrappeSite> siteOpt = frappeSiteRepository.findByIdAndIsDeletedFalse(siteId);
                
                if (siteOpt.isPresent()) {
                    FrappeSite site = siteOpt.get();
                    if (site.getSendgridApiKey() != null && !site.getSendgridApiKey().trim().isEmpty()) {
                        log.info("SendGrid API key resolved from site: {} (siteId={})", site.getSiteName(), siteId);
                        return new SendGrid(site.getSendgridApiKey().trim());
                    } else {
                        log.info("Site {} has no SendGrid API key; falling back to global sendgrid_config (siteId={})", site.getSiteName(), siteId);
                    }
                } else {
                    log.warn("FrappeSite not found for siteId: {}; falling back to global sendgrid_config", siteId);
                }
            } catch (Exception e) {
                log.error("Error resolving SendGrid API key from site {}: {}; falling back to global config", payload.getSiteId(), e.getMessage());
                // Fall through to global config
            }
        } else {
            log.info("Payload has no siteId; using global sendgrid_config for SendGrid API key");
        }
        
        // Priority 2: Get API key from global SendGridConfig (sendgrid_config table)
        Optional<SendGridConfig> config = sendGridConfigRepository.findByIsDeletedFalse();
        if (config.isPresent() && config.get().getSendgridApiKey() != null 
                && !config.get().getSendgridApiKey().trim().isEmpty()) {
            log.info("SendGrid API key resolved from global sendgrid_config (id={})", config.get().getId());
            return new SendGrid(config.get().getSendgridApiKey().trim());
        }
        if (config.isEmpty()) {
            log.warn("No active row in sendgrid_config (findByIsDeletedFalse returned empty)");
        } else if (config.get().getSendgridApiKey() == null || config.get().getSendgridApiKey().trim().isEmpty()) {
            log.warn("Active sendgrid_config row has null or empty sendgrid_api_key");
        }
        
        // Priority 3: Use fallback from config file (environment variable)
        if (fallbackSendGridApiKey != null && !fallbackSendGridApiKey.trim().isEmpty() 
                && !fallbackSendGridApiKey.equals("your-sendgrid-api-key")) {
            log.info("SendGrid API key from environment/config fallback (SENDGRID_API_KEY)");
            return new SendGrid(fallbackSendGridApiKey.trim());
        }
        
        log.warn("No SendGrid API key found: tried site(s), global sendgrid_config, and env fallback");
        return null;
    }
    
    /**
     * Get from email address.
     * Priority: 1. Payload, 2. Database config, 3. Default
     */
    private String getFromEmail(NotificationPayload payload) {
        if (payload.getFromEmail() != null && !payload.getFromEmail().trim().isEmpty()) {
            return payload.getFromEmail().trim();
        }
        
        Optional<SendGridConfig> config = sendGridConfigRepository.findByIsDeletedFalse();
        if (config.isPresent() && config.get().getEmailFromAddress() != null 
                && !config.get().getEmailFromAddress().trim().isEmpty()) {
            return config.get().getEmailFromAddress().trim();
        }
        
        return defaultFromEmail;
    }
    
    /**
     * Get from name.
     * Priority: 1. Payload, 2. Database config, 3. Default
     */
    private String getFromName(NotificationPayload payload) {
        if (payload.getFromName() != null && !payload.getFromName().trim().isEmpty()) {
            return payload.getFromName().trim();
        }
        
        Optional<SendGridConfig> config = sendGridConfigRepository.findByIsDeletedFalse();
        if (config.isPresent() && config.get().getEmailFromName() != null 
                && !config.get().getEmailFromName().trim().isEmpty()) {
            return config.get().getEmailFromName().trim();
        }
        
        return defaultFromName;
    }
}

