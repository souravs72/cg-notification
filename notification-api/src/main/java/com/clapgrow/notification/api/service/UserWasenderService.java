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
     * Get WASender API key from user session.
     * Always fetches fresh data from database to avoid stale session data.
     */
    public Optional<String> getApiKeyFromSession(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        
        // Always fetch from database to get fresh data
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
            log.error("Error getting API key from database", e);
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
     * Get current user from session.
     * Always fetches fresh data from database to avoid stale session data.
     * 
     * @deprecated Use UserService.getCurrentUser() instead for consistency
     */
    @Deprecated
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
            log.error("Error getting user from database", e);
            return Optional.empty();
        }
    }
}

