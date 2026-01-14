package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.entity.User;
import com.clapgrow.notification.api.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    
    private final UserService userService;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid email or password");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(@RequestParam(required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "auth/register";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, 
                       @RequestParam String password,
                       HttpSession session,
                       Model model) {
        try {
            User user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

            if (!userService.validatePassword(password, user.getPasswordHash())) {
                model.addAttribute("error", "Invalid email or password");
                return "auth/login";
            }

            // Set user in session
            session.setAttribute("userId", user.getId().toString());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("wasenderApiKey", user.getWasenderApiKey());
            session.setAttribute("subscriptionType", user.getSubscriptionType());
            session.setAttribute("subscriptionStatus", user.getSubscriptionStatus());
            session.setAttribute("sessionsAllowed", user.getSessionsAllowed());
            session.setAttribute("sessionsUsed", user.getSessionsUsed());

            log.info("User logged in: {}", user.getEmail());
            return "redirect:/admin/dashboard";

        } catch (Exception e) {
            log.error("Login error for email: {}", email, e);
            model.addAttribute("error", "Invalid email or password");
            return "auth/login";
        }
    }

    @PostMapping("/register")
    public String register(@RequestParam String email,
                          @RequestParam String password,
                          @RequestParam String confirmPassword,
                          @RequestParam String wasenderApiKey,
                          HttpSession session,
                          Model model) {
        try {
            // Validate inputs
            if (email == null || email.trim().isEmpty()) {
                model.addAttribute("error", "Email is required");
                return "auth/register";
            }

            if (password == null || password.length() < 6) {
                model.addAttribute("error", "Password must be at least 6 characters");
                return "auth/register";
            }

            if (!password.equals(confirmPassword)) {
                model.addAttribute("error", "Passwords do not match");
                return "auth/register";
            }

            if (wasenderApiKey == null || wasenderApiKey.trim().isEmpty()) {
                model.addAttribute("error", "WASender API key is required");
                return "auth/register";
            }

            // Register user (this will validate WASender API key and get subscription info)
            User user = userService.registerUser(email, password, wasenderApiKey);

            // Set user in session
            session.setAttribute("userId", user.getId().toString());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("wasenderApiKey", user.getWasenderApiKey());
            session.setAttribute("subscriptionType", user.getSubscriptionType());
            session.setAttribute("subscriptionStatus", user.getSubscriptionStatus());
            session.setAttribute("sessionsAllowed", user.getSessionsAllowed());
            session.setAttribute("sessionsUsed", user.getSessionsUsed());

            log.info("User registered: {}", user.getEmail());
            return "redirect:/admin/dashboard";

        } catch (IllegalArgumentException e) {
            log.error("Registration error: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
            return "auth/register";
        } catch (Exception e) {
            log.error("Registration error for email: {}", email, e);
            model.addAttribute("error", "Registration failed: " + e.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        String email = (String) session.getAttribute("userEmail");
        session.invalidate();
        log.info("User logged out: {}", email);
        return "redirect:/auth/login";
    }
    
    @PostMapping("/logout")
    public String logoutPost(HttpSession session) {
        String email = (String) session.getAttribute("userEmail");
        session.invalidate();
        log.info("User logged out: {}", email);
        return "redirect:/auth/login";
    }

    @GetMapping("/api/current-user")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpSession session) {
        Map<String, Object> userInfo = new HashMap<>();
        
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        userInfo.put("userId", userId);
        userInfo.put("email", session.getAttribute("userEmail"));
        userInfo.put("subscriptionType", session.getAttribute("subscriptionType"));
        userInfo.put("subscriptionStatus", session.getAttribute("subscriptionStatus"));
        userInfo.put("sessionsAllowed", session.getAttribute("sessionsAllowed"));
        userInfo.put("sessionsUsed", session.getAttribute("sessionsUsed"));

        return ResponseEntity.ok(userInfo);
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data
    public static class RegisterRequest {
        private String email;
        private String password;
        private String confirmPassword;
        private String wasenderApiKey;
    }
}

