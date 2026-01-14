package com.clapgrow.notification.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "wasender_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WasenderConfig extends BaseAuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "wasender_api_key", nullable = false, length = 255)
    private String wasenderApiKey;
}















