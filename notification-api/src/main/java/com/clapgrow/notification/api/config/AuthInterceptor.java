package com.clapgrow.notification.api.config;

import com.clapgrow.notification.api.annotation.AdminApi;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authentication interceptor for admin dashboard pages.
 * 
 * ⚠️ SECURITY MODEL:
 * - Admin API endpoints (annotated with @AdminApi) are excluded from session checks;
 *   authentication is enforced centrally by AdminAuthAspect
 * - Admin dashboard pages require session authentication
 * - Path-based detection is deprecated in favor of @AdminApi annotation
 * 
 * This ensures security intent is explicit and reduces risk of accidentally exposing endpoints.
 */
@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CRITICAL: Allow OPTIONS requests (CORS preflight) to pass through
        // CORS preflight requests hit the interceptor before controllers
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        
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
            // ✅ NEW: Annotation-based API detection (preferred)
            // If handler is a method annotated with @AdminApi, skip session check
            // AdminAuthAspect enforces authentication centrally
            if (handler instanceof HandlerMethod method) {
                // Check method-level annotation first (more specific)
                if (method.hasMethodAnnotation(AdminApi.class)) {
                    log.debug("Admin API endpoint detected via @AdminApi method annotation: {}", path);
                    return true; // AdminAuthAspect enforces authentication
                }
                // Check class-level annotation (applies to all methods in controller)
                if (method.getBeanType().isAnnotationPresent(AdminApi.class)) {
                    log.debug("Admin API endpoint detected via @AdminApi class annotation: {}", path);
                    return true; // AdminAuthAspect enforces authentication
                }
            }
            
            // ⚠️ LEGACY: Path-based detection (for backward compatibility)
            // TODO: Remove legacy path-based detection by 2026-03-01
            // Migrate all admin API endpoints to use @AdminApi annotation
            // Only matches explicit /admin/api/** prefix to avoid false positives like /admin/campaigns/apiary
            if (path.startsWith("/admin/api/")) {
                log.debug("Admin API endpoint detected via path pattern (legacy): {}", path);
                return true; // AdminAuthAspect enforces authentication
            }
            
            // For non-API admin routes (dashboard pages), require session authentication
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("userId") == null) {
                // CRITICAL: Enhanced logging for redirect loop debugging
                String cookies = request.getHeader("Cookie");
                log.warn("Unauthorized dashboard access to {} - no session or userId missing. " +
                    "Session={}, userId={}, cookies={}", 
                    path, 
                    session != null ? session.getId() : "null", 
                    session != null ? session.getAttribute("userId") : "null",
                    cookies != null ? "present" : "missing");
                // Clear stale JSESSIONID so browser gets a clean slate on next login
                // (task restart loses in-memory sessions; old cookie would keep triggering 401)
                Cookie clearCookie = new Cookie("JSESSIONID", "");
                clearCookie.setMaxAge(0);
                clearCookie.setPath("/");
                response.addCookie(clearCookie);
                response.sendRedirect("/auth/login");
                return false;
            }
            
            // CRITICAL: Log successful session check for debugging redirect loops
            log.debug("Authorized dashboard access to {} - userId={}, sessionId={}", 
                path, session.getAttribute("userId"), session.getId());
        }

        return true;
    }
}

