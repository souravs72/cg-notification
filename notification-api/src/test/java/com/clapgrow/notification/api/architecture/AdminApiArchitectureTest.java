package com.clapgrow.notification.api.architecture;

import com.clapgrow.notification.api.annotation.AdminApi;
import com.clapgrow.notification.api.annotation.RequireAdminAuth;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Architecture tests to enforce @AdminApi and @RequireAdminAuth annotation usage.
 * 
 * These tests ensure compile-time enforcement of security policies:
 * - All admin API endpoints must be annotated with @AdminApi
 * - All @AdminApi methods must also have @RequireAdminAuth (enforces aspect usage)
 * - Prevents accidental exposure of admin endpoints without explicit annotation
 * 
 * Run with: mvn test -Dtest=AdminApiArchitectureTest
 */
@AnalyzeClasses(packages = "com.clapgrow.notification.api.controller")
public class AdminApiArchitectureTest {

    /**
     * Rule: All methods in admin controllers that handle admin API paths
     * (e.g., /admin/api/... or /admin/.../api/...)
     * must be annotated with @AdminApi (either method-level or class-level).
     * 
     * This prevents accidental exposure of admin API endpoints without
     * explicit security intent declaration.
     */
    @ArchTest
    static final ArchRule adminApiEndpointsMustHaveAnnotation = methods()
            .that()
            .areAnnotatedWith(GetMapping.class)
            .or().areAnnotatedWith(PostMapping.class)
            .or().areAnnotatedWith(PutMapping.class)
            .or().areAnnotatedWith(DeleteMapping.class)
            .or().areAnnotatedWith(PatchMapping.class)
            .and()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(Controller.class)
            .and()
            .areDeclaredInClassesThat()
            .haveSimpleNameContaining("Admin")
            .should(new AdminApiAnnotationCondition())
            .because("All admin API endpoints must be explicitly annotated with @AdminApi " +
                    "to ensure security intent is clear and prevent accidental exposure");

    /**
     * Rule: Every method annotated with @AdminApi must also have @RequireAdminAuth.
     * 
     * This ensures that the AdminAuthAspect is always invoked, making authentication
     * enforcement non-optional. Without this, someone could accidentally write:
     * 
     * @AdminApi
     * @PostMapping("/api/xyz")
     * public ResponseEntity<?> unsafe() { ... }
     * 
     * And bypass authentication.
     */
    @ArchTest
    static final ArchRule adminApiMustHaveRequireAdminAuth = methods()
            .that()
            .areAnnotatedWith(AdminApi.class)
            .or()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(AdminApi.class)
            .should(new RequireAdminAuthCondition())
            .because("Every @AdminApi method must also have @RequireAdminAuth " +
                    "to ensure AdminAuthAspect enforces authentication (makes aspect non-optional)");

    /**
     * Custom condition to check if a method or its declaring class has @AdminApi annotation.
     */
    private static class AdminApiAnnotationCondition extends ArchCondition<JavaMethod> {
        AdminApiAnnotationCondition() {
            super("be annotated with @AdminApi");
        }

        @Override
        public void check(JavaMethod method, ConditionEvents events) {
            // Check if method is in a path that suggests admin API
            String methodPath = getMethodPath(method);
            if (methodPath != null && methodPath.contains("/admin/") && methodPath.contains("/api/")) {
                // Check if method or class has @AdminApi
                boolean hasMethodAnnotation = method.isAnnotatedWith(AdminApi.class);
                boolean hasClassAnnotation = method.getOwner().isAnnotatedWith(AdminApi.class);
                
                if (!hasMethodAnnotation && !hasClassAnnotation) {
                    events.add(SimpleConditionEvent.violated(
                            method,
                            String.format("Method %s.%s handles admin API path %s but is not annotated with @AdminApi",
                                    method.getOwner().getName(), method.getName(), methodPath)
                    ));
                }
            }
        }

        private String getMethodPath(JavaMethod method) {
            // Check method-level mapping annotations
            if (method.isAnnotatedWith(GetMapping.class)) {
                String[] values = method.getAnnotationOfType(GetMapping.class).value();
                return values.length > 0 ? values[0] : "";
            }
            if (method.isAnnotatedWith(PostMapping.class)) {
                String[] values = method.getAnnotationOfType(PostMapping.class).value();
                return values.length > 0 ? values[0] : "";
            }
            if (method.isAnnotatedWith(PutMapping.class)) {
                String[] values = method.getAnnotationOfType(PutMapping.class).value();
                return values.length > 0 ? values[0] : "";
            }
            if (method.isAnnotatedWith(DeleteMapping.class)) {
                String[] values = method.getAnnotationOfType(DeleteMapping.class).value();
                return values.length > 0 ? values[0] : "";
            }
            if (method.isAnnotatedWith(PatchMapping.class)) {
                String[] values = method.getAnnotationOfType(PatchMapping.class).value();
                return values.length > 0 ? values[0] : "";
            }
            
            // Check class-level RequestMapping
            JavaClass owner = method.getOwner();
            if (owner.isAnnotatedWith(RequestMapping.class)) {
                String[] classPaths = owner.getAnnotationOfType(RequestMapping.class).value();
                if (classPaths.length > 0) {
                    return classPaths[0];
                }
            }
            
            return null;
        }
    }

    /**
     * Custom condition to check if @AdminApi methods also have @RequireAdminAuth.
     */
    private static class RequireAdminAuthCondition extends ArchCondition<JavaMethod> {
        RequireAdminAuthCondition() {
            super("be annotated with @RequireAdminAuth");
        }

        @Override
        public void check(JavaMethod method, ConditionEvents events) {
            // Check if method has @AdminApi annotation
            boolean hasMethodAdminApi = method.isAnnotatedWith(AdminApi.class);
            // Check if class has @AdminApi annotation
            boolean hasClassAdminApi = method.getOwner().isAnnotatedWith(AdminApi.class);
            
            // Only check if method or class has @AdminApi
            if (hasMethodAdminApi || hasClassAdminApi) {
                // Method must have @RequireAdminAuth (class-level doesn't count for this rule)
                if (!method.isAnnotatedWith(RequireAdminAuth.class)) {
                    events.add(SimpleConditionEvent.violated(
                            method,
                            String.format("Method %s.%s is annotated with @AdminApi but missing @RequireAdminAuth. " +
                                    "This makes authentication enforcement optional. Add @RequireAdminAuth to ensure AdminAuthAspect enforces authentication.",
                                    method.getOwner().getName(), method.getName())
                    ));
                }
            }
        }
    }
}
