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
            // Note: changeSessionId() modifies the session in place, so we can continue using the same session object
            session.setAttribute("userId", user.getId().toString());

            log.info("User logged in: {} (session regenerated for security)", user.getEmail());
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

            // CRITICAL SECURITY FIX: Regenerate session ID to prevent session fixation attacks
            // This ensures that even if an attacker knows the session ID before registration,
            // they cannot use it after the user authenticates
            request.changeSessionId();
            
            // Set only userId in session - all other data fetched from DB on demand
            // Note: changeSessionId() modifies the session in place, so we can continue using the same session object
            session.setAttribute("userId", user.getId().toString());

            log.info("User registered: {} (session regenerated for security)", user.getEmail());
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

