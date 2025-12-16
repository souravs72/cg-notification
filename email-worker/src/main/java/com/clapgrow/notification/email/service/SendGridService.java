package com.clapgrow.notification.email.service;

import com.clapgrow.notification.email.model.NotificationPayload;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class SendGridService {
    
    private final SendGrid defaultSendGrid;
    
    @Value("${sendgrid.api.key}")
    private String defaultSendGridApiKey;
    
    @Value("${sendgrid.from.email: noreply@example.com}")
    private String defaultFromEmail;
    
    @Value("${sendgrid.from.name:Notification Service}")
    private String defaultFromName;

    public SendEmailResult sendEmail(NotificationPayload payload) {
        try {
            SendGrid sendGrid = getSendGridForPayload(payload);
            
            Email from = new Email(
                payload.getFromEmail() != null ? payload.getFromEmail() : defaultFromEmail,
                payload.getFromName() != null ? payload.getFromName() : defaultFromName
            );
            
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
    
    private SendGrid getSendGridForPayload(NotificationPayload payload) {
        if (payload.getSendgridApiKey() != null && !payload.getSendgridApiKey().trim().isEmpty()) {
            return new SendGrid(payload.getSendgridApiKey());
        }
        return defaultSendGrid;
    }
}

