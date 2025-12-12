package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.dto.SiteRegistrationRequest;
import com.clapgrow.notification.api.dto.SiteRegistrationResponse;
import com.clapgrow.notification.api.service.SiteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/site")
@RequiredArgsConstructor
public class SiteController {
    
    private final SiteService siteService;

    @PostMapping("/register")
    public ResponseEntity<SiteRegistrationResponse> registerSite(
            @Valid @RequestBody SiteRegistrationRequest request) {
        SiteRegistrationResponse response = siteService.registerSite(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

