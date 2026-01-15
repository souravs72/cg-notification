package com.clapgrow.notification.email.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "sendgrid_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SendGridConfig extends BaseAuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "sendgrid_api_key", nullable = false, length = 255)
    private String sendgridApiKey;
    
    @Column(name = "email_from_address", length = 255)
    private String emailFromAddress;
    
    @Column(name = "email_from_name", length = 255)
    private String emailFromName;
}



