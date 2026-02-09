package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.config.SubscriptionDefaultsProperties;
import com.clapgrow.notification.api.entity.User;
import com.clapgrow.notification.api.enums.SubscriptionStatus;
import com.clapgrow.notification.api.enums.SubscriptionType;
import com.clapgrow.notification.api.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class UserService {
    
    // Actor constants for audit trail
    private static final String ACTOR_SYSTEM = "SYSTEM";
    
    // SCALE: When subscription tiers grow, extract subscription policy logic to:
    // - SubscriptionPolicyResolver service
    // - Handles tier-based limits, pricing, features
    // - Centralizes subscription logic for easier maintenance and testing
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessagingSubscriptionService subscriptionService;
    private final SubscriptionDefaultsProperties subscriptionDefaults;

    public UserService(UserRepository userRepository, 
                       PasswordEncoder passwordEncoder,
                       MessagingSubscriptionService subscriptionService,
                       SubscriptionDefaultsProperties subscriptionDefaults) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.subscriptionService = subscriptionService;
        this.subscriptionDefaults = subscriptionDefaults;
    }
    
    /**
     * Apply default subscription values to a user.
     * Centralizes subscription initialization to reduce repetition.
     */
    private void applyDefaultSubscription(User user) {
        user.setSubscriptionType(SubscriptionType.FREE_TRIAL.name());
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE.name());
        user.setSessionsAllowed(subscriptionDefaults.getFreeTrialSessions());
    }

    /**
     * Find user by email.
     * 
     * INVARIANT: Emails are always normalized (lowercase, trimmed) before persistence and lookup.
     * This ensures consistent behavior regardless of how the email is provided.
     * 
     * @param email User email (will be normalized before lookup)
     * @return Optional containing user if found, empty otherwise
     */
    public Optional<User> findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }
        String normalizedEmail = email.toLowerCase().trim();
        return userRepository.findByEmailAndIsDeletedFalse(normalizedEmail);
    }

    public Optional<User> findById(UUID id) {
        return userRepository.findByIdAndIsDeletedFalse(id);
    }

    public boolean validatePassword(String rawPassword, String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }

    /**
     * Register a new user.
     * 
     * Handles race conditions via database unique constraint (uq_users_email_active).
     * Catches DataIntegrityViolationException if concurrent registration occurs.
     * 
     * @param email User email (case-insensitive)
     * @param password User password
     * @param messagingApiKey Optional messaging provider API key (e.g., WASender)
     * @return Registered user
     * @throws BadRequestException if email already exists or validation fails
     */
    public User registerUser(String email, String password, String messagingApiKey) {
        // Normalize email before validation and persistence
        // INVARIANT: Emails are always normalized (lowercase, trimmed) before persistence and lookup.
        String normalizedEmail = email.toLowerCase().trim();
        
        // Validate email uniqueness first (outside transaction to avoid holding connection)
        // Note: This is a best-effort check. The database unique constraint is the real protection.
        if (userRepository.existsByEmailAndIsDeletedFalse(normalizedEmail)) {
            throw new BadRequestException("User with this email already exists");
        }

        // Fetch subscription info BEFORE starting transaction to avoid holding DB connection during HTTP call
        // ⚠️ SECURITY: NEVER log messagingApiKey - treat as secret
        MessagingSubscriptionService.SubscriptionInfo subscriptionInfo = null;
        boolean saveApiKey = false; // Track whether key is valid and should be saved
        
        if (messagingApiKey != null && !messagingApiKey.trim().isEmpty()) {
            try {
                // Validate messaging provider API key and get subscription info (outside transaction)
                // NEVER log messagingApiKey parameter - only log subscription info
                subscriptionInfo = subscriptionService.getSubscriptionInfo(messagingApiKey.trim());
                saveApiKey = true; // Key is valid - safe to save
                log.info("Fetched subscription info from {} for registration: type={}, status={}, sessionsAllowed={}", 
                    subscriptionService.getProviderName(),
                    subscriptionInfo.getSubscriptionType(), subscriptionInfo.getSubscriptionStatus(), 
                    subscriptionInfo.getSessionsAllowed());
            } catch (SubscriptionValidationException e) {
                // Branch on error category for smart retry logic
                if (e.getCategory() == SubscriptionValidationException.ErrorCategory.TEMPORARY) {
                    // Temporary errors: provider downtime, rate limits, network issues - fallback gracefully
                    log.warn("{} API key validation failed (temporary) during registration for {}: {}. " +
                        "User will be created with default subscription. API key can be added later.", 
                        subscriptionService.getProviderName(), email, e.getMessage());
                    saveApiKey = false; // Don't save key on temporary errors
                    subscriptionInfo = null; // Will use default subscription
                } else if (e.getCategory() == SubscriptionValidationException.ErrorCategory.PERMANENT || 
                          e.getCategory() == SubscriptionValidationException.ErrorCategory.AUTH) {
                    // Permanent/Auth errors: invalid key format, auth revoked - fail fast, don't save invalid key
                    log.error("{} API key validation failed (permanent/auth) during registration for {}: {}. " +
                        "Registration will proceed without API key.", 
                        subscriptionService.getProviderName(), email, e.getMessage());
                    saveApiKey = false; // Don't save invalid/revoked keys
                    subscriptionInfo = null; // Will use default subscription
                }
            } catch (Exception e) {
                // Catch any other unexpected exceptions - treat as temporary
                log.warn("{} API key validation failed (unexpected error) during registration for {}: {}. " +
                    "User will be created with default subscription. API key can be added later.", 
                    subscriptionService.getProviderName(), email, e.getMessage());
                saveApiKey = false; // Don't save key on unexpected errors
                subscriptionInfo = null; // Will use default subscription
            }
        }

        // Now perform database operations in transaction
        // Only pass messagingApiKey if validation succeeded (saveApiKey == true)
        try {
            return saveUserWithSubscription(normalizedEmail, password, saveApiKey ? messagingApiKey : null, subscriptionInfo);
        } catch (DataIntegrityViolationException e) {
            // Handle race condition: concurrent registration with same email
            // Database unique constraint (uq_users_email_active) prevents duplicate
            log.warn("Registration failed due to duplicate email (race condition detected): {}", normalizedEmail);
            throw new BadRequestException("User with this email already exists", e);
        }
    }

    @Transactional
    private User saveUserWithSubscription(String email, String password, String messagingApiKey, 
                                         MessagingSubscriptionService.SubscriptionInfo subscriptionInfo) {
        // INVARIANT: Email is already normalized before this method is called.
        // This method receives normalized email from registerUser().
        User user = new User();
        user.setEmail(email); // Email is already normalized
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setSessionsUsed(0);
        user.setCreatedBy(ACTOR_SYSTEM);
        user.setIsDeleted(false);

        // Apply subscription info (from HTTP call or defaults)
        if (subscriptionInfo != null && messagingApiKey != null && !messagingApiKey.trim().isEmpty()) {
            // FUTURE: Rename wasenderApiKey field to messagingApiKey (requires database migration)
            // FUTURE: When supporting multiple providers per user, store provider name:
            // user.setMessagingProvider(subscriptionService.getProviderName());
            user.setWasenderApiKey(messagingApiKey.trim());
            user.setSubscriptionType(subscriptionInfo.getSubscriptionType());
            user.setSubscriptionStatus(subscriptionInfo.getSubscriptionStatus());
            
            // Apply system defaults if provider didn't send session limits
            // WASender does not reliably expose limits, so we use system defaults to avoid drift
            if (subscriptionInfo.getSessionsAllowed() != null) {
                user.setSessionsAllowed(subscriptionInfo.getSessionsAllowed());
            } else {
            // Provider didn't send limits - use configurable system defaults based on subscription type
            SubscriptionType type = SubscriptionType.valueOf(subscriptionInfo.getSubscriptionType());
            user.setSessionsAllowed(type == SubscriptionType.FREE_TRIAL 
                ? subscriptionDefaults.getFreeTrialSessions() 
                : subscriptionDefaults.getPaidSessions());
            }
            
            log.info("Registered new user: {} with {} API key and subscription: {}", 
                email, subscriptionService.getProviderName(), subscriptionInfo.getSubscriptionType());
        } else {
            // No API key provided or validation failed - use default subscription info
            user.setWasenderApiKey(null);
            applyDefaultSubscription(user);
            
            log.info("Registered new user: {} without messaging API key (default subscription)", email);
        }

        user = userRepository.save(user);
        return user;
    }

    /**
     * Update messaging provider API key for a user and refresh subscription information.
     * 
     * ⚠️ SECURITY: NEVER log messagingApiKey parameter - treat as secret.
     * Only log provider name and user email for audit purposes.
     * 
     * @param userId User ID
     * @param messagingApiKey Messaging provider API key (NEVER log this value)
     * @return Updated user entity
     * @throws BadRequestException if user not found or API key is empty
     * @throws ServiceUnavailableException if provider is temporarily unavailable
     */
    @Transactional
    public User updateMessagingApiKey(UUID userId, String messagingApiKey) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
            .orElseThrow(() -> new BadRequestException("User not found"));

        if (messagingApiKey == null || messagingApiKey.trim().isEmpty()) {
            throw new BadRequestException("Messaging API key is required");
        }

        // Validate and update subscription info
        // NEVER log messagingApiKey - treat as secret
        MessagingSubscriptionService.SubscriptionInfo subscriptionInfo;
        try {
            subscriptionInfo = subscriptionService.getSubscriptionInfo(messagingApiKey);
        } catch (SubscriptionValidationException e) {
            // Branch on error category for smart retry logic
            if (e.getCategory() == SubscriptionValidationException.ErrorCategory.TEMPORARY) {
                // Temporary errors: provider downtime, rate limits - throw to allow retry
                log.warn("{} API key validation failed (temporary) for user {}: {}. " +
                    "Please try again later.", subscriptionService.getProviderName(), user.getEmail(), e.getMessage());
                throw new ServiceUnavailableException("Temporary error validating API key: " + e.getMessage(), e);
            } else if (e.getCategory() == SubscriptionValidationException.ErrorCategory.PERMANENT || 
                      e.getCategory() == SubscriptionValidationException.ErrorCategory.AUTH) {
                // Permanent/Auth errors: invalid key format, auth revoked - fail fast, don't save
                log.error("{} API key validation failed (permanent/auth) for user {}: {}. " +
                    "Invalid key will not be saved.", subscriptionService.getProviderName(), user.getEmail(), e.getMessage());
                throw new BadRequestException("Invalid API key: " + e.getMessage(), e);
            } else {
                throw e; // Re-throw if unknown category
            }
        }

        // FUTURE: Rename wasenderApiKey field to messagingApiKey (requires database migration)
        // FUTURE: When supporting multiple providers per user, store provider name:
        // user.setMessagingProvider(subscriptionService.getProviderName());
        user.setWasenderApiKey(messagingApiKey.trim());
        user.setSubscriptionType(subscriptionInfo.getSubscriptionType());
        user.setSubscriptionStatus(subscriptionInfo.getSubscriptionStatus());
        
        // Apply system defaults if provider didn't send session limits
        // WASender does not reliably expose limits, so we use system defaults to avoid drift
        if (subscriptionInfo.getSessionsAllowed() != null) {
            user.setSessionsAllowed(subscriptionInfo.getSessionsAllowed());
        } else {
            // Provider didn't send limits - use configurable system defaults based on subscription type
            SubscriptionType type = SubscriptionType.valueOf(subscriptionInfo.getSubscriptionType());
            user.setSessionsAllowed(type == SubscriptionType.FREE_TRIAL 
                ? subscriptionDefaults.getFreeTrialSessions() 
                : subscriptionDefaults.getPaidSessions());
        }
        
        user.setUpdatedBy(ACTOR_SYSTEM);

        user = userRepository.save(user);
        // Log provider name and user email only - NEVER log the API key
        log.info("Updated {} API key for user: {}", subscriptionService.getProviderName(), user.getEmail());
        
        return user;
    }

    /**
     * INTERNAL USE ONLY - FALLBACK METHOD.
     * Bypasses external validation for messaging API key.
     * 
     * ⚠️ SECURITY WARNING: This method does NOT validate the API key with the provider.
     * It should ONLY be used as a fallback when:
     * 1. External validation fails (network issues, provider downtime)
     * 2. We still need to save the key for later validation
     * 
     * Should NOT be used for:
     * - User-facing endpoints without validation attempt first
     * - Bypassing security checks
     * - Storing untrusted user input
     * 
     * ⚠️ SECURITY: NEVER log messagingApiKey parameter - treat as secret.
     * 
     * Current usage: AdminController uses this as fallback when updateMessagingApiKey() fails.
     */
    @Transactional
    public User updateMessagingApiKeyWithoutValidation(UUID userId, String messagingApiKey) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
            .orElseThrow(() -> new BadRequestException("User not found"));

        if (messagingApiKey == null || messagingApiKey.trim().isEmpty()) {
            throw new BadRequestException("Messaging API key is required");
        }

        // Save API key without external validation (used as fallback)
        // NEVER log messagingApiKey - treat as secret
        user.setWasenderApiKey(messagingApiKey.trim());
        user.setUpdatedBy(ACTOR_SYSTEM);

        user = userRepository.save(user);
        // Log user email only - NEVER log the API key
        log.info("Updated messaging API key (without validation) for user: {}", user.getEmail());
        
        return user;
    }

    /**
     * Atomically update sessionsUsed for a user.
     * Uses database-level update to prevent race conditions.
     * 
     * @param userId User ID
     * @param sessionsUsed New sessions used count
     * @throws BadRequestException if user not found or sessionsUsed is negative
     */
    @Transactional
    public void updateSessionsUsed(UUID userId, int sessionsUsed) {
        if (sessionsUsed < 0) {
            throw new BadRequestException("sessionsUsed cannot be negative");
        }
        
        int updated = userRepository.updateSessionsUsed(userId, sessionsUsed, ACTOR_SYSTEM);
        
        if (updated == 0) {
            throw new BadRequestException("User not found or is deleted: " + userId);
        }
        
        // SCALE: When moving to scale, add optimistic locking here to prevent race conditions
        // Add @Version field to User entity and handle OptimisticLockException
        // Example: @Version private Long version; in User entity
        // Then use: userRepository.save(user) and catch OptimisticLockException
    }
    
    /**
     * Check if user has reached their session limit.
     * 
     * SCALE: When moving to scale, enforce session limits at usage time (when creating sessions):
     * 
     * Example enforcement:
     * if (user.getSessionsUsed() >= user.getSessionsAllowed()) {
     *     throw new IllegalStateException("Session limit exceeded");
     * }
     * 
     * This should be called before incrementing sessionsUsed when creating new WhatsApp sessions.
     * 
     * @param user User entity to check
     * @return true if user has reached their session limit
     */
    public boolean hasReachedSessionLimit(User user) {
        return user.getSessionsUsed() >= user.getSessionsAllowed();
    }

    /**
     * Refresh subscription information from the messaging provider.
     * 
     * Failure isolation: If the provider is down or returns an error, the method logs
     * a warning and returns without updating the user. This prevents transaction failures
     * during cron jobs and allows the system to continue operating with stale subscription data.
     * 
     * @param userId User ID to refresh subscription for
     */
    @Transactional
    public void refreshSubscriptionInfo(UUID userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
            .orElseThrow(() -> new BadRequestException("User not found"));

        if (user.getWasenderApiKey() == null || user.getWasenderApiKey().trim().isEmpty()) {
            log.warn("Cannot refresh subscription info for user {}: no messaging API key", user.getEmail());
            return;
        }

        try {
            // NEVER log user.getWasenderApiKey() - treat as secret
            MessagingSubscriptionService.SubscriptionInfo subscriptionInfo = 
                subscriptionService.getSubscriptionInfo(user.getWasenderApiKey());

            user.setSubscriptionType(subscriptionInfo.getSubscriptionType());
            user.setSubscriptionStatus(subscriptionInfo.getSubscriptionStatus());
            
            // Apply system defaults if provider didn't send session limits
            // WASender does not reliably expose limits, so we use system defaults to avoid drift
            if (subscriptionInfo.getSessionsAllowed() != null) {
                user.setSessionsAllowed(subscriptionInfo.getSessionsAllowed());
            } else {
            // Provider didn't send limits - use configurable system defaults based on subscription type
            SubscriptionType type = SubscriptionType.valueOf(subscriptionInfo.getSubscriptionType());
            user.setSessionsAllowed(type == SubscriptionType.FREE_TRIAL 
                ? subscriptionDefaults.getFreeTrialSessions() 
                : subscriptionDefaults.getPaidSessions());
            }
            
            user.setUpdatedBy(ACTOR_SYSTEM);

            userRepository.save(user);
            // Log provider name and user email only - NEVER log the API key
            log.info("Refreshed subscription info from {} for user: {}", 
                subscriptionService.getProviderName(), user.getEmail());
        } catch (SubscriptionValidationException e) {
            // Branch on error category for smart retry logic
            if (e.getCategory() == SubscriptionValidationException.ErrorCategory.TEMPORARY) {
                // Temporary errors: provider downtime, rate limits - continue with stale data
                log.warn("Failed to refresh subscription info from {} for user {} (temporary): {}. " +
                    "User will continue with existing subscription data.",
                    subscriptionService.getProviderName(), user.getEmail(), e.getMessage());
            } else if (e.getCategory() == SubscriptionValidationException.ErrorCategory.PERMANENT || 
                      e.getCategory() == SubscriptionValidationException.ErrorCategory.AUTH) {
                // Permanent/Auth errors: invalid key format, auth revoked - mark key as invalid
                log.error("Failed to refresh subscription info from {} for user {} (permanent/auth): {}. " +
                    "Clearing invalid API key.", subscriptionService.getProviderName(), user.getEmail(), e.getMessage());
                user.setWasenderApiKey(null); // Clear invalid key
                user.setUpdatedBy(ACTOR_SYSTEM);
                userRepository.save(user);
            }
        } catch (Exception e) {
            // Catch any other unexpected exceptions - treat as temporary
            log.warn("Failed to refresh subscription info from {} for user {} (unexpected error): {}. " +
                "User will continue with existing subscription data.",
                subscriptionService.getProviderName(), user.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Get current user ID from session.
     * Centralized method to avoid session parsing drift across services.
     * 
     * @param session HTTP session containing userId
     * @return Optional containing user ID, empty if not authenticated or invalid
     */
    public Optional<UUID> getCurrentUserId(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return Optional.empty();
        }
        
        try {
            return Optional.of(UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            log.error("Error parsing user ID from session: {}", userId, e);
            return Optional.empty();
        }
    }

    /**
     * Get current user from session, fetching fresh data from database.
     * This ensures we always have up-to-date user information, not stale session data.
     * 
     * @param session HTTP session containing userId
     * @return User entity with fresh data from database
     * @throws IllegalStateException if user is not authenticated or not found
     */
    public User getCurrentUser(HttpSession session) {
        UUID userId = getCurrentUserId(session)
            .orElseThrow(() -> new IllegalStateException("User not authenticated: no userId in session"));
        
        return userRepository.findByIdAndIsDeletedFalse(userId)
            .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
    }
}
