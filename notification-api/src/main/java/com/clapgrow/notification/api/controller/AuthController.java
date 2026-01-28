package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.entity.User;
import com.clapgrow.notification.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
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
    public String loginPage(@RequestParam(required = false) String error, 
                           HttpServletRequest request,
                           Model model) {
        // Check session without creating a new one (use getSession(false))
        // This prevents redirect loops if session cookie isn't working
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            String userId = (String) session.getAttribute("userId");
            log.info("User already logged in (userId={}, sessionId={}), redirecting to dashboard", 
                userId, session.getId());
            return "redirect:/admin/dashboard";
        }
        
        // CRITICAL: Log when login page is accessed without session for debugging
        String cookies = request.getHeader("Cookie");
        log.debug("Login page accessed - session={}, cookies={}", 
            session != null ? session.getId() : "null", 
            cookies != null ? "present" : "missing");
        
        if (error != null) {
            model.addAttribute("error", "Invalid email or password");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(@RequestParam(required = false) String error, 
                              HttpServletRequest request,
                              Model model) {
        // If user is already logged in, redirect to dashboard (prevent registration while logged in)
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            log.debug("User already logged in, redirecting to dashboard");
            return "redirect:/admin/dashboard";
        }
        
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "auth/register";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, 
                       @RequestParam String password,
                       HttpServletRequest request,
                       HttpSession session,
                       Model model) {
        try {
            User user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

            if (!userService.validatePassword(password, user.getPasswordHash())) {
                model.addAttribute("error", "Invalid email or password");
                return "auth/login";
            }

            // CRITICAL SECURITY FIX: Regenerate session ID to prevent session fixation attacks
            // This ensures that even if an attacker knows the session ID before login,
            // they cannot use it after the user authenticates
            request.changeSessionId();
            
            // Set only userId in session - all other data fetched from DB on demand
            session.setAttribute("userId", user.getId().toString());
            session.setMaxInactiveInterval(2592000); // 30 days (matches application.yml max-age)
            
            // Access session ID for logging/debugging
            String sessionId = session.getId();
            session.setAttribute("_sessionInitialized", "true");
            
            log.info("User logged in: {} (userId={}, sessionId={}, session regenerated for security)", 
                user.getEmail(), user.getId(), sessionId);

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
                          @RequestParam(required = false) String wasenderApiKey,
                          HttpServletRequest request,
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

            // Register user (WASender API key is optional - can be added later)
            User user = userService.registerUser(email, password, wasenderApiKey);

            // For registration, we don't need changeSessionId() because
            // registration is a new-user flow (no pre-existing session fixation risk).
            
            // Set userId in session - this will create/use the existing session
            // The session cookie will be set properly by Spring's session management
            session.setAttribute("userId", user.getId().toString());
            session.setMaxInactiveInterval(2592000); // 30 days (matches application.yml max-age)
            
            // Access session ID for logging/debugging
            String sessionId = session.getId();
            session.setAttribute("_sessionInitialized", "true");
            
            log.info("User registered: {} (userId={}, sessionId={})", user.getEmail(), user.getId(), sessionId);

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
        String userId = (String) session.getAttribute("userId");
        session.invalidate();
        log.info("User logged out: userId={}", userId);
        return "redirect:/auth/login";
    }
    
    @PostMapping("/logout")
    public String logoutPost(HttpSession session) {
        String userId = (String) session.getAttribute("userId");
        session.invalidate();
        log.info("User logged out: userId={}", userId);
        return "redirect:/auth/login";
    }

    @GetMapping("/api/current-user")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpSession session) {
        try {
            // Fetch fresh user data from database
            User user = userService.getCurrentUser(session);
            
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", user.getId().toString());
            userInfo.put("email", user.getEmail());
            userInfo.put("subscriptionType", user.getSubscriptionType());
            userInfo.put("subscriptionStatus", user.getSubscriptionStatus());
            userInfo.put("sessionsAllowed", user.getSessionsAllowed());
            userInfo.put("sessionsUsed", user.getSessionsUsed());

            return ResponseEntity.ok(userInfo);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
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

