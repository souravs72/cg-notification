package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.dto.WasenderApiKeyRequest;
import com.clapgrow.notification.api.entity.User;
import com.clapgrow.notification.api.service.WasenderConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.aop.AopAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerWasenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private com.clapgrow.notification.api.service.AdminService adminService;

    @MockBean
    private com.clapgrow.notification.api.service.SiteService siteService;

    @MockBean
    private com.clapgrow.notification.api.service.WasenderQRServiceClient wasenderQRServiceClient;

    @MockBean
    private com.clapgrow.notification.api.service.AdminAuthService adminAuthService;

    @MockBean
    private WasenderConfigService wasenderConfigService;

    @MockBean
    private com.clapgrow.notification.api.service.SendGridConfigService sendGridConfigService;

    @MockBean
    private com.clapgrow.notification.api.service.UserService userService;

    @MockBean
    private com.clapgrow.notification.api.service.UserWasenderService userWasenderService;

    @MockBean
    private com.clapgrow.notification.api.service.WhatsAppSessionService whatsAppSessionService;

    @Test
    void testSaveWasenderApiKey_Success() throws Exception {
        WasenderApiKeyRequest request = new WasenderApiKeyRequest();
        request.setWasenderApiKey("test-api-key-12345");

        // Mock user
        UUID testUserId = UUID.randomUUID();
        User testUser = new User();
        testUser.setId(testUserId);
        testUser.setEmail("test@example.com");

        doNothing().when(wasenderConfigService).saveApiKey(anyString());
        when(userService.getCurrentUser(any())).thenReturn(testUser);
        when(userService.updateMessagingApiKey(any(UUID.class), anyString())).thenReturn(testUser);

        mockMvc.perform(post("/admin/api/wasender/api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .sessionAttr("userId", testUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.message").value("WASender PAT saved successfully"));

        verify(wasenderConfigService).saveApiKey("test-api-key-12345");
        verify(userService).getCurrentUser(any());
        verify(userService).updateMessagingApiKey(eq(testUserId), eq("test-api-key-12345"));
    }

    @Test
    void testSaveWasenderApiKey_EmptyKey_ReturnsBadRequest() throws Exception {
        WasenderApiKeyRequest request = new WasenderApiKeyRequest();
        request.setWasenderApiKey("");

        doThrow(new IllegalArgumentException("WASender API key cannot be empty"))
                .when(wasenderConfigService).saveApiKey(anyString());

        mockMvc.perform(post("/admin/api/wasender/api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testGetWasenderApiKeyStatus_Configured() throws Exception {
        when(wasenderConfigService.isConfigured()).thenReturn(true);

        mockMvc.perform(get("/admin/api/wasender/api-key/status")
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.message").value("WASender API key is configured"));
    }

    @Test
    void testGetWasenderApiKeyStatus_NotConfigured() throws Exception {
        when(wasenderConfigService.isConfigured()).thenReturn(false);

        mockMvc.perform(get("/admin/api/wasender/api-key/status")
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.message").value("WASender API key is not configured. Please configure it first."));
    }

    @Test
    void testCreateSite_WithoutApiKey_Returns428() throws Exception {
        when(userWasenderService.isConfigured(any())).thenReturn(false);

        com.clapgrow.notification.api.dto.SiteRegistrationRequest request = 
            new com.clapgrow.notification.api.dto.SiteRegistrationRequest();
        request.setSiteName("Test Site");

        mockMvc.perform(post("/admin/api/sites/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.error").value("WASender API key is not configured"));

        verify(siteService, never()).registerSite(any());
    }
}














