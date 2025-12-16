package com.clapgrow.notification.email.service;

import com.clapgrow.notification.email.entity.SendGridConfig;
import com.clapgrow.notification.email.model.NotificationPayload;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class SendGridService {
    
    private final SendGridConfigRepository sendGridConfigRepository;
    
    @Value("${sendgrid.api.key:}")
    private String fallbackSendGridApiKey;
    
    @Value("${sendgrid.from.email:noreply@example.com}")
    private String defaultFromEmail;
    
    @Value("${sendgrid.from.name:Notification Service}")
    private String defaultFromName;

    public SendEmailResult sendEmail(NotificationPayload payload) {
        try {
            // Get SendGrid instance with API key (priority: payload > database > config file)
            SendGrid sendGrid = getSendGridForPayload(payload);
            
            if (sendGrid == null) {
                return SendEmailResult.failure("SendGrid API key is not configured. Please configure it in the admin panel.");
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
                return SendEmailResult.success();
            } else {
                String errorMessage = String.format("SendGrid API error: Status %d - %s", 
                    response.getStatusCode(), 
                    response.getBody() != null ? response.getBody() : "No response body");
                log.error("Failed to send email to {}: {}", 
                    payload.getRecipient(), errorMessage);
                return SendEmailResult.failure(errorMessage);
            }
            
        } catch (IOException e) {
            String errorMessage = String.format("SendGrid API IOException: %s", e.getMessage());
            log.error("Error sending email via SendGrid to {}", payload.getRecipient(), e);
            return SendEmailResult.failure(errorMessage);
        } catch (Exception e) {
            String errorMessage = String.format("Unexpected error sending email: %s", e.getMessage());
            log.error("Unexpected error sending email via SendGrid to {}", payload.getRecipient(), e);
            return SendEmailResult.failure(errorMessage);
        }
    }
    
    /**
     * Get SendGrid instance with API key.
     * Priority: 1. Payload API key, 2. Database config, 3. Config file fallback
     */
    private SendGrid getSendGridForPayload(NotificationPayload payload) {
        // Priority 1: Use API key from payload if provided
        if (payload.getSendgridApiKey() != null && !payload.getSendgridApiKey().trim().isEmpty()) {
            log.debug("Using SendGrid API key from payload");
            return new SendGrid(payload.getSendgridApiKey().trim());
        }
        
        // Priority 2: Get API key from database
        Optional<SendGridConfig> config = sendGridConfigRepository.findByIsDeletedFalse();
        if (config.isPresent() && config.get().getSendgridApiKey() != null 
                && !config.get().getSendgridApiKey().trim().isEmpty()) {
            log.debug("Using SendGrid API key from database");
            return new SendGrid(config.get().getSendgridApiKey().trim());
        }
        
        // Priority 3: Use fallback from config file
        if (fallbackSendGridApiKey != null && !fallbackSendGridApiKey.trim().isEmpty() 
                && !fallbackSendGridApiKey.equals("your-sendgrid-api-key")) {
            log.debug("Using SendGrid API key from configuration file");
            return new SendGrid(fallbackSendGridApiKey.trim());
        }
        
        log.warn("No SendGrid API key found in payload, database, or configuration");
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

