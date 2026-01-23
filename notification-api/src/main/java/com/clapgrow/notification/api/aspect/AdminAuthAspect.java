package com.clapgrow.notification.api.aspect;

import com.clapgrow.notification.api.dto.ApiResponse;
import com.clapgrow.notification.api.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * Aspect to enforce admin authentication for methods annotated with @RequireAdminAuth.
 * 
 * This aspect:
 * - Extracts HttpSession and X-Admin-Key header from HttpServletRequest (via RequestContextHolder)
 * - Validates authentication (session OR admin key)
 * - Returns standardized 401 error response if authentication fails
 * - Allows method execution if authentication succeeds
 * 
 * Benefits:
 * - Centralized authentication logic
 * - Consistent error responses
 * - Impossible to forget authentication on new endpoints
 * - Cleaner controller code (no manual auth checks)
 * - Robust: Works even if controller method signature changes
 * - Works for async/proxy cases (doesn't rely on method parameters)
 * 
 * ⚠️ DESIGN DECISION: Only intercept @RequireAdminAuth (not @AdminApi).
 * 
 * Rationale:
 * - ArchUnit enforces that every @AdminApi method must also have @RequireAdminAuth
 * - This prevents double-enforcement and allows @AdminApi to be used for other purposes
 *   (e.g., marking endpoints for AuthInterceptor exclusion) without triggering authentication
 * - Single responsibility: @RequireAdminAuth = authentication enforcement,
 *   @AdminApi = AuthInterceptor exclusion marker
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAuthAspect {
    
    private final AdminAuthService adminAuthService;
    
    /**
     * Intercept methods annotated with @RequireAdminAuth.
     * 
     * Note: We only intercept @RequireAdminAuth, not @AdminApi, even though ArchUnit
     * enforces that @AdminApi methods must also have @RequireAdminAuth. This prevents
     * accidental double-enforcement and allows @AdminApi to be used for other purposes
     * (like AuthInterceptor exclusion) without triggering authentication.
     */
    @Around("@annotation(com.clapgrow.notification.api.annotation.RequireAdminAuth)")
    public Object enforceAdminAuth(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // Extract HttpServletRequest from RequestContextHolder (more robust than method parameters)
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("No request attributes found for method: {}.{}", 
                method.getDeclaringClass().getSimpleName(), method.getName());
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Internal server error: request context not available"));
        }
        
        HttpServletRequest request = attributes.getRequest();
        HttpSession session = request.getSession(false);
        String adminKey = request.getHeader("X-Admin-Key");
        
        // Validate authentication (session OR admin key)
        if (!isAuthenticated(session, adminKey)) {
            log.debug("Admin authentication failed for method: {}.{}", 
                method.getDeclaringClass().getSimpleName(), method.getName());
            return ResponseEntity.status(401)
                .body(ApiResponse.error("Authentication required"));
        }
        
        // Authentication successful - proceed with method execution
        return joinPoint.proceed();
    }
    
    /**
     * Check if user is authenticated via session OR admin key.
     * 
     * @param session HTTP session (may be null)
     * @param adminKey Admin API key from header (may be null)
     * @return true if authenticated, false otherwise
     */
    private boolean isAuthenticated(HttpSession session, String adminKey) {
        // Check session authentication first
        if (session != null && session.getAttribute("userId") != null) {
            return true;
        }
        
        // If no session, require admin key
        if (adminKey == null || adminKey.trim().isEmpty()) {
            return false;
        }
        
        // Validate admin key
        try {
            adminAuthService.validateAdminKey(adminKey);
            return true;
        } catch (SecurityException e) {
            log.debug("Admin key validation failed: {}", e.getMessage());
            return false;
        }
    }
}

