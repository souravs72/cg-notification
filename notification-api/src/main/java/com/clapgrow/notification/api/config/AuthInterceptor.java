package com.clapgrow.notification.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow access to auth pages and static resources
        String path = request.getRequestURI();
        if (path.startsWith("/auth/") || 
            path.startsWith("/static/") || 
            path.startsWith("/css/") || 
            path.startsWith("/js/") ||
            path.equals("/") ||
            path.equals("/favicon.ico")) {
            return true;
        }

        // Check if user is authenticated for admin routes
        if (path.startsWith("/admin/")) {
            // For API endpoints, let the controller handle authentication
            // (supports both session and X-Admin-Key header authentication)
            if (path.contains("/api/")) {
                return true;
            }
            
            // For non-API admin routes (dashboard pages), require session authentication
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("userId") == null) {
                response.sendRedirect("/auth/login");
                return false;
            }
        }

        return true;
    }
}

