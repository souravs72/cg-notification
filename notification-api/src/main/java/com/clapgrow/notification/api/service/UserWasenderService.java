package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.entity.User;
import com.clapgrow.notification.api.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserWasenderService {
    
    private final UserRepository userRepository;

    /**
     * Get WASender API key from user session
     * Checks session attribute first (for immediate availability after saving),
     * then falls back to database (for persistence across requests)
     */
    public Optional<String> getApiKeyFromSession(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        
        // First, check session attribute (set immediately after saving PAT)
        String sessionApiKey = (String) session.getAttribute("wasenderApiKey");
        if (sessionApiKey != null && !sessionApiKey.trim().isEmpty()) {
            return Optional.of(sessionApiKey.trim());
        }
        
        // Fall back to database (for persistence across requests)
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return Optional.empty();
        }
        
        try {
            User user = userRepository.findByIdAndIsDeletedFalse(UUID.fromString(userId))
                .orElse(null);
            
            if (user != null && user.getWasenderApiKey() != null && !user.getWasenderApiKey().trim().isEmpty()) {
                // Also update session attribute for future requests
                session.setAttribute("wasenderApiKey", user.getWasenderApiKey());
                return Optional.of(user.getWasenderApiKey());
            }
        } catch (Exception e) {
            log.error("Error getting API key from session", e);
        }
        
        return Optional.empty();
    }

    /**
     * Check if user has WASender API key configured
     */
    public boolean isConfigured(HttpSession session) {
        return getApiKeyFromSession(session).isPresent();
    }

    /**
     * Get current user from session
     */
    public Optional<User> getCurrentUser(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return Optional.empty();
        }
        
        try {
            return userRepository.findByIdAndIsDeletedFalse(UUID.fromString(userId));
        } catch (Exception e) {
            log.error("Error getting user from session", e);
            return Optional.empty();
        }
    }
}

