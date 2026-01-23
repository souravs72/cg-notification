package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.dto.SiteRegistrationRequest;
import com.clapgrow.notification.api.dto.SiteRegistrationResponse;
import com.clapgrow.notification.api.service.SiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/site")
@RequiredArgsConstructor
@Tag(name = "Site Management", description = "API endpoints for site registration and management")
public class SiteController {
    
    private final SiteService siteService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new site",
            description = "Registers a new site and returns an API key that can be used to send notifications."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Site registered successfully",
                    content = @Content(schema = @Schema(implementation = SiteRegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    public ResponseEntity<SiteRegistrationResponse> registerSite(
            @Valid @RequestBody SiteRegistrationRequest request) {
        SiteRegistrationResponse response = siteService.registerSite(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

