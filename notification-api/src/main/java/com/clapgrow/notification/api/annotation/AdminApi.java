package com.clapgrow.notification.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for admin API endpoints.
 * 
 * Can be applied at class or method level:
 * - Class-level: All methods in the controller are treated as admin APIs
 * - Method-level: Only the annotated method is treated as an admin API
 * 
 * Endpoints annotated with @AdminApi are excluded from AuthInterceptor session checks
 * and must handle authentication at the controller level (typically via X-Admin-Key header).
 * 
 * This makes security intent explicit rather than relying on path patterns,
 * reducing the risk of accidentally exposing endpoints without authentication.
 * 
 * Example (method-level):
 * <pre>
 * {@code
 * @PostMapping("/admin/campaigns/send-test")
 * @AdminApi
 * public ResponseEntity<?> sendTest(...) {
 *     // Controller must validate X-Admin-Key or session
 * }
 * }
 * </pre>
 * 
 * Example (class-level):
 * <pre>
 * {@code
 * @Controller
 * @RequestMapping("/admin/api")
 * @AdminApi
 * public class AdminApiController {
 *     // All methods in this controller are admin APIs
 * }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminApi {
}

