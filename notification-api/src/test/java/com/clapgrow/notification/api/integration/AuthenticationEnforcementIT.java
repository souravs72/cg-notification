package com.clapgrow.notification.api.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests to enforce authentication requirements.
 * These tests ensure that:
 * 1. /admin/** dashboard pages redirect without session
 * 2. /admin/api/** endpoints fail without X-Admin-Key header
 * 
 * This turns discipline into enforcement - prevents accidental public exposure
 * of admin endpoints if someone forgets to add authentication.
 */
@AutoConfigureMockMvc
@Transactional
@DisplayName("Authentication Enforcement Integration Tests")
class AuthenticationEnforcementIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${admin.api-key:test-admin-key}")
    private String adminApiKey;

    @Test
    @DisplayName("/admin/** should redirect to login without session")
    void testAdminDashboardRedirectsWithoutSession() throws Exception {
        // Test various admin dashboard endpoints
        mockMvc.perform(get("/admin/dashboard"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/auth/login"));

        mockMvc.perform(get("/admin/sites"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/auth/login"));

        mockMvc.perform(get("/admin/campaigns"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    @DisplayName("/admin/api/** should fail with 401 without X-Admin-Key")
    void testAdminApiFailsWithoutAdminKey() throws Exception {
        // Test admin API endpoints without X-Admin-Key header (use real endpoints: /admin/api/metrics, /admin/api/messages/recent)
        mockMvc.perform(get("/admin/api/metrics"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Authentication required"));

        mockMvc.perform(get("/admin/api/messages/recent"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    @DisplayName("/admin/api/** should fail with 401 with invalid X-Admin-Key")
    void testAdminApiFailsWithInvalidAdminKey() throws Exception {
        mockMvc.perform(get("/admin/api/metrics")
                .header("X-Admin-Key", "invalid-key"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("/admin/api/** should succeed with valid X-Admin-Key")
    void testAdminApiSucceedsWithValidAdminKey() throws Exception {
        // Use real admin API endpoint that requires X-Admin-Key
        mockMvc.perform(get("/admin/api/metrics")
                .header("X-Admin-Key", adminApiKey))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/admin/api/** accepts session auth for dashboard JS (session OR X-Admin-Key)")
    void testAdminApiAcceptsSessionAuth() throws Exception {
        // Admin API allows session so dashboard pages can fetch /admin/api/metrics with credentials: same-origin
        mockMvc.perform(get("/admin/api/metrics")
                .sessionAttr("userId", "test-user-id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSites").exists());
    }

    @Test
    @DisplayName("/admin/** non-API endpoints should NOT accept X-Admin-Key")
    void testAdminDashboardDoesNotAcceptAdminKey() throws Exception {
        // Dashboard pages should only accept session auth, not API key
        // This ensures separation of concerns
        mockMvc.perform(get("/admin/dashboard")
                .header("X-Admin-Key", adminApiKey))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/auth/login"));
    }
}

