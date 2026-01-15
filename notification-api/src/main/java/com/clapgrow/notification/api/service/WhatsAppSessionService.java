package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.entity.WhatsAppSession;
import com.clapgrow.notification.api.repository.WhatsAppSessionRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppSessionService {
    
    private final WhatsAppSessionRepository sessionRepository;
    private final WasenderQRService wasenderQRService;
    private final UserWasenderService userWasenderService;

    /**
     * Get current user ID from session
     */
    private Optional<UUID> getCurrentUserId(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(userId));
        } catch (Exception e) {
            log.error("Error parsing user ID from session", e);
            return Optional.empty();
        }
    }

    /**
     * Create or update a WhatsApp session
     */
    @Transactional
    public WhatsAppSession saveSession(String sessionId, String sessionName, String phoneNumber, 
                                      Boolean accountProtection, Boolean logMessages,
                                      String webhookUrl, String[] webhookEvents,
                                      HttpSession httpSession) {
        UUID userId = getCurrentUserId(httpSession)
            .orElseThrow(() -> new IllegalStateException("User not found in session"));
        
        Optional<WhatsAppSession> existing = sessionRepository
            .findByUserIdAndSessionNameAndIsDeletedFalse(userId, sessionName);
        
        WhatsAppSession session;
        if (existing.isPresent()) {
            session = existing.get();
            session.setSessionId(sessionId);
            session.setPhoneNumber(phoneNumber);
            session.setAccountProtection(accountProtection);
            session.setLogMessages(logMessages);
            session.setWebhookUrl(webhookUrl);
            session.setWebhookEvents(webhookEvents);
        } else {
            session = new WhatsAppSession();
            session.setUserId(userId);
            session.setSessionId(sessionId);
            session.setSessionName(sessionName);
            session.setPhoneNumber(phoneNumber);
            session.setAccountProtection(accountProtection);
            session.setLogMessages(logMessages);
            session.setWebhookUrl(webhookUrl);
            session.setWebhookEvents(webhookEvents);
            session.setStatus("PENDING");
        }
        
        return sessionRepository.save(session);
    }

    /**
     * Update session status and API key when session connects
     */
    @Transactional
    public void updateSessionOnConnect(String sessionIdentifier, HttpSession httpSession) {
        UUID userId = getCurrentUserId(httpSession)
            .orElseThrow(() -> new IllegalStateException("User not found in session"));
        
        String userApiKey = userWasenderService.getApiKeyFromSession(httpSession)
            .orElseThrow(() -> new IllegalStateException("WASender API key not found in session"));
        
        // Get session details from WASender API
        Map<String, Object> sessionDetails = wasenderQRService.getSessionDetails(sessionIdentifier, userApiKey);
        
        if (sessionDetails == null || !Boolean.TRUE.equals(sessionDetails.get("success"))) {
            log.warn("Could not fetch session details for {}", sessionIdentifier);
            return;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) sessionDetails.get("data");
        if (data == null) {
            log.warn("Session details data is null for {}", sessionIdentifier);
            return;
        }
        
        String sessionId = data.get("id") != null ? data.get("id").toString() : sessionIdentifier;
        String sessionName = data.get("name") != null ? data.get("name").toString() : null;
        String status = data.get("status") != null ? data.get("status").toString() : "UNKNOWN";
        
        // Look for API key in the response - it might be in different fields
        String sessionApiKey = null;
        if (data.get("api_key") != null) {
            sessionApiKey = data.get("api_key").toString();
        } else if (data.get("apiKey") != null) {
            sessionApiKey = data.get("apiKey").toString();
        } else if (data.get("token") != null) {
            sessionApiKey = data.get("token").toString();
        }
        
        // Find the session in our database
        Optional<WhatsAppSession> sessionOpt = Optional.empty();
        if (sessionName != null) {
            sessionOpt = sessionRepository.findByUserIdAndSessionNameAndIsDeletedFalse(userId, sessionName);
        }
        if (sessionOpt.isEmpty() && sessionId != null) {
            sessionOpt = sessionRepository.findByUserIdAndSessionIdAndIsDeletedFalse(userId, sessionId);
        }
        
        if (sessionOpt.isPresent()) {
            WhatsAppSession session = sessionOpt.get();
            session.setStatus(status);
            if (sessionApiKey != null && !sessionApiKey.trim().isEmpty()) {
                session.setSessionApiKey(sessionApiKey.trim());
                log.info("Stored API key for session {}: {}", sessionName, sessionApiKey.substring(0, Math.min(10, sessionApiKey.length())) + "...");
            }
            if ("CONNECTED".equalsIgnoreCase(status) || "connected".equalsIgnoreCase(status)) {
                session.setConnectedAt(LocalDateTime.now());
            }
            sessionRepository.save(session);
        } else {
            log.warn("Session not found in database for identifier: {}", sessionIdentifier);
        }
    }

    /**
     * Get all sessions for current user
     */
    public List<WhatsAppSession> getUserSessions(HttpSession httpSession) {
        UUID userId = getCurrentUserId(httpSession)
            .orElseThrow(() -> new IllegalStateException("User not found in session"));
        return sessionRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
    }

    /**
     * Get connected sessions for current user
     */
    public List<WhatsAppSession> getConnectedSessions(HttpSession httpSession) {
        UUID userId = getCurrentUserId(httpSession)
            .orElseThrow(() -> new IllegalStateException("User not found in session"));
        return sessionRepository.findByUserIdAndIsDeletedFalseAndStatusOrderByCreatedAtDesc(userId, "CONNECTED");
    }

    /**
     * Get session by name for current user
     */
    public Optional<WhatsAppSession> getSessionByName(String sessionName, HttpSession httpSession) {
        UUID userId = getCurrentUserId(httpSession)
            .orElseThrow(() -> new IllegalStateException("User not found in session"));
        return sessionRepository.findByUserIdAndSessionNameAndIsDeletedFalse(userId, sessionName);
    }

    /**
     * Get session API key by session name
     */
    public Optional<String> getSessionApiKey(String sessionName, HttpSession httpSession) {
        return getSessionByName(sessionName, httpSession)
            .map(WhatsAppSession::getSessionApiKey)
            .filter(key -> key != null && !key.trim().isEmpty());
    }

    /**
     * Update session API key by session ID or name
     */
    @Transactional
    public void updateSessionApiKey(String sessionIdentifier, String sessionApiKey, HttpSession httpSession) {
        UUID userId = getCurrentUserId(httpSession)
            .orElseThrow(() -> new IllegalStateException("User not found in session"));
        
        Optional<WhatsAppSession> sessionOpt = Optional.empty();
        
        // Try to find by session ID first (if it's numeric)
        if (sessionIdentifier != null && sessionIdentifier.trim().matches("^\\d+$")) {
            sessionOpt = sessionRepository.findByUserIdAndSessionIdAndIsDeletedFalse(userId, sessionIdentifier.trim());
            log.debug("Looking for session by ID: {} for user: {}", sessionIdentifier, userId);
        }
        
        // If not found, try by session name
        if (sessionOpt.isEmpty()) {
            sessionOpt = sessionRepository.findByUserIdAndSessionNameAndIsDeletedFalse(userId, sessionIdentifier);
            log.debug("Looking for session by name: {} for user: {}", sessionIdentifier, userId);
        }
        
        if (sessionOpt.isPresent()) {
            WhatsAppSession session = sessionOpt.get();
            String oldApiKey = session.getSessionApiKey();
            session.setSessionApiKey(sessionApiKey != null ? sessionApiKey.trim() : null);
            sessionRepository.save(session);
            log.info("Updated session API key for session: {} (ID: {}). Old key: {}, New key: {}", 
                    session.getSessionName(), session.getSessionId(),
                    oldApiKey != null ? oldApiKey.substring(0, Math.min(10, oldApiKey.length())) + "..." : "null",
                    sessionApiKey != null ? sessionApiKey.substring(0, Math.min(10, sessionApiKey.length())) + "..." : "null");
        } else {
            log.error("Session not found in database for identifier: {} (user: {}). Cannot update API key.", 
                    sessionIdentifier, userId);
            // List all sessions for this user to help debug
            List<WhatsAppSession> allSessions = sessionRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
            log.warn("Available sessions for user {}: {}", userId, 
                    allSessions.stream()
                        .map(s -> String.format("ID=%s, Name=%s", s.getSessionId(), s.getSessionName()))
                        .collect(java.util.stream.Collectors.joining(", ")));
            throw new IllegalStateException("Session not found for identifier: " + sessionIdentifier + 
                    ". Available sessions: " + allSessions.size());
        }
    }

    /**
     * Sync sessions from WASender API response to database
     */
    @Transactional
    public void syncSessionsFromWasender(Map<String, Object> wasenderResponse, HttpSession httpSession) {
        UUID userId = getCurrentUserId(httpSession)
            .orElseThrow(() -> new IllegalStateException("User not found in session"));
        
        Object dataObj = wasenderResponse.get("data");
        if (dataObj == null) {
            return;
        }
        
        List<Map<String, Object>> sessions;
        if (dataObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sessionsList = (List<Map<String, Object>>) dataObj;
            sessions = sessionsList;
        } else if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
            if (dataMap.containsKey("sessions") && dataMap.get("sessions") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> sessionsList = (List<Map<String, Object>>) dataMap.get("sessions");
                sessions = sessionsList;
            } else {
                sessions = List.of(dataMap);
            }
        } else {
            return;
        }
        
        for (Map<String, Object> sessionData : sessions) {
            try {
                String sessionId = sessionData.get("id") != null ? sessionData.get("id").toString() : null;
                String sessionName = sessionData.get("name") != null ? sessionData.get("name").toString() : null;
                String status = sessionData.get("status") != null ? sessionData.get("status").toString() : "PENDING";
                String phoneNumber = sessionData.get("phone_number") != null 
                    ? sessionData.get("phone_number").toString() 
                    : (sessionData.get("phoneNumber") != null ? sessionData.get("phoneNumber").toString() : null);
                
                if (sessionName == null && sessionId == null) {
                    continue;
                }
                
                // Find or create session
                Optional<WhatsAppSession> existing = Optional.empty();
                if (sessionName != null) {
                    existing = sessionRepository.findByUserIdAndSessionNameAndIsDeletedFalse(userId, sessionName);
                }
                if (existing.isEmpty() && sessionId != null) {
                    existing = sessionRepository.findByUserIdAndSessionIdAndIsDeletedFalse(userId, sessionId);
                }
                
                WhatsAppSession session;
                if (existing.isPresent()) {
                    session = existing.get();
                    // Update status and session ID if changed
                    if (sessionId != null) {
                        session.setSessionId(sessionId);
                    }
                    session.setStatus(status);
                    if (phoneNumber != null) {
                        session.setPhoneNumber(phoneNumber);
                    }
                } else {
                    // Create new session
                    session = new WhatsAppSession();
                    session.setUserId(userId);
                    session.setSessionId(sessionId != null ? sessionId : sessionName);
                    session.setSessionName(sessionName != null ? sessionName : sessionId);
                    session.setStatus(status);
                    session.setPhoneNumber(phoneNumber);
                }
                
                sessionRepository.save(session);
            } catch (Exception e) {
                log.warn("Error syncing session from WASender: {}", e.getMessage());
            }
        }
    }
}

