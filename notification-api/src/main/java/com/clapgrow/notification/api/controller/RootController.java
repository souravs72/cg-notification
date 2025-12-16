package com.clapgrow.notification.api.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

@Controller
public class RootController {
    
    @GetMapping("/")
    public String root() {
        return "redirect:/auth/login";
    }
    
    @GetMapping("/favicon.ico")
    public ResponseEntity<Resource> favicon() throws IOException {
        // Try SVG first, fallback to a simple response if not found
        try {
            Resource resource = new ClassPathResource("static/favicon.svg");
            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("image/svg+xml"))
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                        .body(resource);
            }
        } catch (Exception e) {
            // Fall through to 204 No Content
        }
        // Return 204 No Content if favicon not found (prevents error)
        return ResponseEntity.noContent().build();
    }
}

