package com.clapgrow.notification.whatsapp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces strict sequential message delivery per WhatsApp session with a mandatory
 * delay between messages. Required for WaSender/WhatsApp session stability.
 *
 * <p>Rules (must not be violated):
 * <ul>
 *   <li>Only ONE message may be sent at a time per session</li>
 *   <li>Messages are sent strictly sequentially, never in parallel</li>
 *   <li>After each message attempt (success or failure), wait the configured delay
 *       before the next message may start</li>
 * </ul>
 *
 * <p>Uses per-session locks; different sessions may send in parallel.
 */
@Component
@Slf4j
public class WhatsAppSessionSequencer {

    /** Default delay in milliseconds between messages (WaSender requirement). */
    public static final long DEFAULT_DELAY_BETWEEN_MESSAGES_MS = 5000L;

    @Value("${whatsapp.bulk.delay-between-messages-ms:" + DEFAULT_DELAY_BETWEEN_MESSAGES_MS + "}")
    private long delayBetweenMessagesMs;

    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    /**
     * Execute a send action for the given session. Ensures only one message is sent
     * at a time per session, and waits the configured delay after the action completes
     * (whether successful or failed) before allowing the next message for that session.
     *
     * @param sessionKey unique key for the WhatsApp session (e.g. session name or "site:" + siteId)
     * @param action     the send action to execute
     * @return the result of the action
     */
    public <T> T executeForSession(String sessionKey, java.util.function.Supplier<T> action) {
        Object lock = sessionLocks.computeIfAbsent(sessionKey, k -> new Object());
        synchronized (lock) {
            try {
                return action.get();
            } finally {
                try {
                    Thread.sleep(delayBetweenMessagesMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted during mandatory delay for session {} - rethrowing", sessionKey);
                    throw new RuntimeException("Interrupted during mandatory delay between messages", e);
                }
            }
        }
    }

    /**
     * Derive a session key from the notification payload. Used to group messages
     * that share the same WhatsApp session for sequential processing.
     *
     * @param whatsappSessionName session name if explicitly set
     * @param siteId              site ID (required for tenant isolation)
     * @return session key for sequencing
     */
    public static String deriveSessionKey(String whatsappSessionName, UUID siteId) {
        if (whatsappSessionName != null && !whatsappSessionName.trim().isEmpty()) {
            return whatsappSessionName.trim();
        }
        if (siteId != null) {
            return "site:" + siteId;
        }
        return "default";
    }
}
