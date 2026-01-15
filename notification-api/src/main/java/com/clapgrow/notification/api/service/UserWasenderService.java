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
     * Prioritizes HttpSession attribute, then falls back to database
     */
    public Optional<String> getApiKeyFromSession(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        
        // First, try to get from HttpSession attribute (set when PAT is saved)
        String patFromSession = (String) session.getAttribute("wasenderApiKey");
        if (patFromSession != null && !patFromSession.trim().isEmpty()) {
            return Optional.of(patFromSession);
        }
        
        // Fallback to database
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return Optional.empty();
        }
        
        try {
            User user = userRepository.findByIdAndIsDeletedFalse(UUID.fromString(userId))
                .orElse(null);
            
            if (user != null && user.getWasenderApiKey() != null && !user.getWasenderApiKey().trim().isEmpty()) {
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

