package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.entity.User;
import com.clapgrow.notification.api.repository.UserRepository;
import com.clapgrow.notification.api.repository.WhatsAppSessionRepository;
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
    private final WhatsAppSessionRepository whatsAppSessionRepository;

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
     * Get session API key for a specific WhatsApp session.
     * This is the preferred method for sending messages - uses session-specific API key instead of PAT.
     * 
     * @param session HTTP session
     * @param sessionName WhatsApp session name (optional - if null, uses first connected session)
     * @return Session API key if available, otherwise falls back to PAT key
     */
    public Optional<String> getSessionApiKey(HttpSession session, String sessionName) {
        if (session == null) {
            return Optional.empty();
        }
        
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return Optional.empty();
        }
        
        UUID userUuid;
        try {
            userUuid = UUID.fromString(userId);
        } catch (Exception e) {
            log.error("Error parsing user ID from session", e);
            return Optional.empty();
        }
        
        // Try to get session-specific API key first (preferred for sending messages)
        if (sessionName != null && !sessionName.trim().isEmpty()) {
            Optional<String> sessionApiKey = whatsAppSessionRepository
                .findByUserIdAndSessionNameAndIsDeletedFalse(userUuid, sessionName.trim())
                .map(s -> s.getSessionApiKey())
                .filter(key -> key != null && !key.trim().isEmpty());
            if (sessionApiKey.isPresent()) {
                log.debug("Using session API key for session: {}", sessionName);
                return sessionApiKey;
            }
        }
        
        // If no session name provided, try to get first connected session's API key
        try {
            var connectedSessions = whatsAppSessionRepository
                .findByUserIdAndIsDeletedFalseAndStatusOrderByCreatedAtDesc(userUuid, "CONNECTED");
            if (!connectedSessions.isEmpty()) {
                String firstSessionApiKey = connectedSessions.get(0).getSessionApiKey();
                if (firstSessionApiKey != null && !firstSessionApiKey.trim().isEmpty()) {
                    log.debug("Using first connected session API key");
                    return Optional.of(firstSessionApiKey);
                }
            }
        } catch (Exception e) {
            log.debug("Could not get connected sessions: {}", e.getMessage());
        }
        
        // Fallback to PAT key (for backward compatibility, but not recommended for sending messages)
        log.debug("Falling back to PAT key (session API key not available)");
        return getApiKeyFromSession(session);
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

