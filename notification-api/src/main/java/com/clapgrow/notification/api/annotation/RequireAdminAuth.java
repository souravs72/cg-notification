package com.clapgrow.notification.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require admin authentication via aspect.
 * 
 * Methods annotated with @RequireAdminAuth will have authentication enforced
 * automatically by AdminAuthAspect. Supports both session-based and API key authentication.
 * 
 * The aspect will:
 * - Extract HttpSession and X-Admin-Key header from method parameters
 * - Validate authentication (session OR admin key)
 * - Return 401 error response if authentication fails
 * - Allow method execution if authentication succeeds
 * 
 * Example:
 * <pre>
 * {@code
 * @PostMapping("/api/send")
 * @RequireAdminAuth
 * public ResponseEntity<ApiResponse<Data>> sendMessage(
 *     @RequestHeader(name = "X-Admin-Key", required = false) String adminKey,
 *     HttpSession session,
 *     @RequestBody Request request) {
 *     // Method body - auth already validated by aspect
 * }
 * }
 * </pre>
 * 
 * Note: This annotation works in conjunction with @AdminApi for AuthInterceptor.
 * @AdminApi excludes the endpoint from session checks, @RequireAdminAuth enforces auth.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdminAuth {
}





