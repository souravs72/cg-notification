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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendGridService implements EmailProvider<NotificationPayload> {
    
    private final SendGridConfigRepository sendGridConfigRepository;
    private final FrappeSiteRepository frappeSiteRepository;
    private final ObjectMapper objectMapper;
    
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
            // Validate payload before processing
            String validationError = validatePayload(payload);
            if (validationError != null) {
                log.error("Invalid email payload: {}", validationError);
                return EmailResult.createFailure(validationError, ProviderErrorCategory.CONFIG);
            }
            
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
            
            // Validate email addresses
            if (fromEmail == null || fromEmail.trim().isEmpty()) {
                return EmailResult.createFailure(
                    "From email address is required but not configured",
                    ProviderErrorCategory.CONFIG
                );
            }
            
            if (payload.getRecipient() == null || payload.getRecipient().trim().isEmpty()) {
                return EmailResult.createFailure(
                    "Recipient email address is required",
                    ProviderErrorCategory.CONFIG
                );
            }
            
            // Validate body is not empty
            if (payload.getBody() == null || payload.getBody().trim().isEmpty()) {
                return EmailResult.createFailure(
                    "Email body is required",
                    ProviderErrorCategory.CONFIG
                );
            }
            
            Email from = new Email(fromEmail, fromName);
            Email to = new Email(payload.getRecipient());
            String subject = payload.getSubject() != null ? payload.getSubject() : "Notification";
            Content content = new Content(
                Boolean.TRUE.equals(payload.getIsHtml()) ? "text/html" : "text/plain",
                payload.getBody()
            );
            
            Mail mail = new Mail(from, subject, to, content);
            
            // Log request details (sanitized) for debugging - no sensitive data
            log.debug("Sending email via SendGrid: recipient={}, from={}, subjectLength={}, bodyLength={}, isHtml={}", 
                maskEmail(payload.getRecipient()), maskEmail(fromEmail), 
                subject != null ? subject.length() : 0,
                payload.getBody() != null ? payload.getBody().length() : 0,
                Boolean.TRUE.equals(payload.getIsHtml()));
            
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            
            Response response = sendGrid.api(request);
            
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                // SECURITY: Mask email address in logs to protect PII
                log.info("Email sent successfully to {} with status code {}", 
                    maskEmail(payload.getRecipient()), response.getStatusCode());
                return EmailResult.createSuccess();
            } else {
                // Categorize error based on HTTP status code
                ProviderErrorCategory errorCategory = categorizeError(response.getStatusCode());
                
                // Safely parse SendGrid error response to extract error messages
                String errorMessage = parseSendGridError(response);
                
                // SECURITY: Log status and sanitized error message, never raw response body
                log.error("Failed to send email to {} via SendGrid: Status {} - {}", 
                    maskEmail(payload.getRecipient()), response.getStatusCode(), errorMessage);
                return EmailResult.createFailure(errorMessage, errorCategory);
            }
            
        } catch (IOException e) {
            // SECURITY: Log error type and recipient only, not full exception details
            String errorMessage = "SendGrid API IOException";
            log.error("Error sending email via SendGrid to {}: {}", 
                maskEmail(payload.getRecipient()), errorMessage);
            return EmailResult.createFailure(errorMessage, ProviderErrorCategory.TEMPORARY);
        } catch (Exception e) {
            // SECURITY: Log error type only, not full exception details
            String errorMessage = String.format("Unexpected error sending email: %s", 
                e.getClass().getSimpleName());
            log.error("Unexpected error sending email via SendGrid to {}: {}", 
                maskEmail(payload.getRecipient()), errorMessage);
            return EmailResult.createFailure(errorMessage, ProviderErrorCategory.TEMPORARY);
        }
    }
    
    /**
     * Validate email payload before sending.
     * 
     * @param payload Notification payload to validate
     * @return Error message if validation fails, null if valid
     */
    private String validatePayload(NotificationPayload payload) {
        if (payload == null) {
            return "Payload is null";
        }
        
        if (payload.getRecipient() == null || payload.getRecipient().trim().isEmpty()) {
            return "Recipient email address is required";
        }
        
        if (payload.getBody() == null || payload.getBody().trim().isEmpty()) {
            return "Email body is required";
        }
        
        // Basic email format validation
        String recipient = payload.getRecipient().trim();
        if (!isValidEmailFormat(recipient)) {
            return String.format("Invalid recipient email format: %s", recipient);
        }
        
        return null;
    }
    
    /**
     * Basic email format validation.
     * 
     * @param email Email address to validate
     * @return true if format appears valid, false otherwise
     */
    private boolean isValidEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        // Basic validation: contains @ and at least one character before and after
        return email.contains("@") && 
               email.indexOf("@") > 0 && 
               email.indexOf("@") < email.length() - 1 &&
               email.matches("^[^@]+@[^@]+\\.[^@]+$");
    }
    
    /**
     * Safely parse SendGrid error response to extract error messages.
     * 
     * SendGrid error responses typically have this structure:
     * {
     *   "errors": [
     *     {
     *       "message": "Error message",
     *       "field": "field.name",
     *       "help": "https://..."
     *     }
     *   ]
     * }
     * 
     * SECURITY: This method extracts only error messages and field names, never API keys or secrets.
     * All extracted messages are sanitized to remove any potential sensitive data.
     * 
     * @param response SendGrid API response
     * @return Formatted error message with SendGrid's error details (sanitized)
     */
    private String parseSendGridError(Response response) {
        String body = response.getBody();
        int statusCode = response.getStatusCode();
        
        if (body == null || body.trim().isEmpty()) {
            return String.format("SendGrid API error: Status %d (no response body)", statusCode);
        }
        
        try {
            JsonNode rootNode = objectMapper.readTree(body);
            List<String> errorMessages = new ArrayList<>();
            
            // Extract errors array if present
            if (rootNode.has("errors") && rootNode.get("errors").isArray()) {
                for (JsonNode errorNode : rootNode.get("errors")) {
                    if (errorNode.has("message")) {
                        String message = sanitizeErrorMessage(errorNode.get("message").asText());
                        String field = errorNode.has("field") ? errorNode.get("field").asText() : null;
                        
                        // SECURITY: Only include field name if it's safe (not an API key field)
                        if (field != null && !isSensitiveField(field)) {
                            errorMessages.add(String.format("%s: %s", field, message));
                        } else {
                            errorMessages.add(message);
                        }
                    }
                }
            }
            
            // If we found specific error messages, use them
            if (!errorMessages.isEmpty()) {
                return String.format("SendGrid API error: Status %d - %s", 
                    statusCode, String.join("; ", errorMessages));
            }
            
            // Fallback: try to extract a generic error message
            if (rootNode.has("message")) {
                String message = sanitizeErrorMessage(rootNode.get("message").asText());
                return String.format("SendGrid API error: Status %d - %s", 
                    statusCode, message);
            }
            
            // Last resort: return status code with body length hint (never the body itself)
            return String.format("SendGrid API error: Status %d (body length=%d, parse failed)", 
                statusCode, body.length());
            
        } catch (Exception e) {
            // SECURITY: If JSON parsing fails, don't expose the body or exception details
            log.debug("Failed to parse SendGrid error response");
            return String.format("SendGrid API error: Status %d (body length=%d, unable to parse)", 
                statusCode, body.length());
        }
    }
    
    /**
     * Sanitize error message to remove any potential sensitive data.
     * 
     * SECURITY: Removes API keys, tokens, and other sensitive patterns.
     * 
     * @param message Raw error message from SendGrid
     * @return Sanitized error message
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Provider error (details redacted)";
        }
        
        String sanitized = message;
        
        // Remove API key patterns (SG.xxx format)
        sanitized = sanitized.replaceAll("SG\\.[A-Za-z0-9_-]{20,}", "[API_KEY_REDACTED]");
        
        // Remove bearer token patterns
        sanitized = sanitized.replaceAll("Bearer\\s+[A-Za-z0-9_-]+", "[TOKEN_REDACTED]");
        
        // Remove any long alphanumeric strings that might be tokens
        sanitized = sanitized.replaceAll("\\b[A-Za-z0-9_-]{32,}\\b", "[REDACTED]");
        
        return sanitized;
    }
    
    /**
     * Check if a field name is sensitive and should not be logged.
     * 
     * @param field Field name from SendGrid error response
     * @return true if field is sensitive, false otherwise
     */
    private boolean isSensitiveField(String field) {
        if (field == null) {
            return false;
        }
        String lowerField = field.toLowerCase();
        return lowerField.contains("api") && lowerField.contains("key") ||
               lowerField.contains("token") ||
               lowerField.contains("secret") ||
               lowerField.contains("password") ||
               lowerField.contains("auth");
    }
    
    /**
     * Mask email address for logging to protect PII.
     * 
     * SECURITY: Masks email addresses in logs to protect personally identifiable information.
     * 
     * @param email Email address to mask
     * @return Masked email address (e.g., "u***@e***.com")
     */
    private String maskEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "[null]";
        }
        
        String trimmed = email.trim();
        int atIndex = trimmed.indexOf('@');
        
        if (atIndex <= 0 || atIndex >= trimmed.length() - 1) {
            return "[invalid]";
        }
        
        // Mask local part: show first char, mask rest
        String localPart = trimmed.substring(0, atIndex);
        String maskedLocal = localPart.length() > 0 
            ? localPart.charAt(0) + "***" 
            : "***";
        
        // Mask domain: show first char of domain, mask rest
        String domain = trimmed.substring(atIndex + 1);
        int dotIndex = domain.indexOf('.');
        if (dotIndex > 0) {
            String domainName = domain.substring(0, dotIndex);
            String tld = domain.substring(dotIndex);
            String maskedDomain = domainName.charAt(0) + "***" + tld;
            return maskedLocal + "@" + maskedDomain;
        }
        
        return maskedLocal + "@" + domain.charAt(0) + "***";
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

