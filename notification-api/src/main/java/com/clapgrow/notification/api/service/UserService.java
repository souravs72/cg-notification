package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.entity.User;
import com.clapgrow.notification.api.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WasenderSubscriptionService wasenderSubscriptionService;

    public UserService(UserRepository userRepository, WasenderSubscriptionService wasenderSubscriptionService) {
        this.userRepository = userRepository;
        this.wasenderSubscriptionService = wasenderSubscriptionService;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailAndIsDeletedFalse(email);
    }

    public Optional<User> findById(UUID id) {
        return userRepository.findByIdAndIsDeletedFalse(id);
    }

    public boolean validatePassword(String rawPassword, String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }

    @Transactional
    public User registerUser(String email, String password, String wasenderApiKey) {
        if (userRepository.existsByEmailAndIsDeletedFalse(email)) {
            throw new IllegalArgumentException("User with this email already exists");
        }

        if (wasenderApiKey == null || wasenderApiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("WASender API key is required");
        }

        // Validate WASender API key and get subscription info
        WasenderSubscriptionService.SubscriptionInfo subscriptionInfo = 
            wasenderSubscriptionService.getSubscriptionInfo(wasenderApiKey);

        User user = new User();
        user.setEmail(email.toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setWasenderApiKey(wasenderApiKey.trim());
        user.setSubscriptionType(subscriptionInfo.getSubscriptionType());
        user.setSubscriptionStatus(subscriptionInfo.getSubscriptionStatus());
        user.setSessionsAllowed(subscriptionInfo.getSessionsAllowed());
        user.setSessionsUsed(0);
        user.setCreatedBy("SYSTEM");
        user.setIsDeleted(false);

        user = userRepository.save(user);
        log.info("Registered new user: {} with subscription: {}", email, subscriptionInfo.getSubscriptionType());
        
        return user;
    }

    public User updateWasenderApiKey(UUID userId, String wasenderApiKey) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (wasenderApiKey == null || wasenderApiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("WASender API key is required");
        }

        // Validate and update subscription info
        WasenderSubscriptionService.SubscriptionInfo subscriptionInfo = 
            wasenderSubscriptionService.getSubscriptionInfo(wasenderApiKey);

        user.setWasenderApiKey(wasenderApiKey.trim());
        user.setSubscriptionType(subscriptionInfo.getSubscriptionType());
        user.setSubscriptionStatus(subscriptionInfo.getSubscriptionStatus());
        user.setSessionsAllowed(subscriptionInfo.getSessionsAllowed());
        user.setUpdatedBy("SYSTEM");

        user = userRepository.save(user);
        log.info("Updated WASender API key for user: {}", user.getEmail());
        
        return user;
    }

    @Transactional
    public void updateSessionsUsed(UUID userId, int sessionsUsed) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        user.setSessionsUsed(sessionsUsed);
        user.setUpdatedBy("SYSTEM");
        userRepository.save(user);
    }

    @Transactional
    public void refreshSubscriptionInfo(UUID userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getWasenderApiKey() == null || user.getWasenderApiKey().trim().isEmpty()) {
            log.warn("Cannot refresh subscription info for user {}: no WASender API key", user.getEmail());
            return;
        }

        WasenderSubscriptionService.SubscriptionInfo subscriptionInfo = 
            wasenderSubscriptionService.getSubscriptionInfo(user.getWasenderApiKey());

        user.setSubscriptionType(subscriptionInfo.getSubscriptionType());
        user.setSubscriptionStatus(subscriptionInfo.getSubscriptionStatus());
        user.setSessionsAllowed(subscriptionInfo.getSessionsAllowed());
        user.setUpdatedBy("SYSTEM");

        userRepository.save(user);
        log.info("Refreshed subscription info for user: {}", user.getEmail());
    }
}

