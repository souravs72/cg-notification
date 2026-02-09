package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.entity.WhatsAppSession;
import com.clapgrow.notification.api.service.AdminAuthService;
import com.clapgrow.notification.api.service.AdminService;
import com.clapgrow.notification.api.service.SiteService;
import com.clapgrow.notification.api.service.UserWasenderService;
import com.clapgrow.notification.api.service.WasenderConfigService;
import com.clapgrow.notification.api.service.WasenderQRServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for AdminController QR code endpoints to ensure:
 * 1. Session ID is preferred over session name
 * 2. Parameter binding works correctly (no BAD_REQUEST errors)
 * 3. Error handling for WASender API responses
 * 4. Session ID is returned in responses
 */
@WebMvcTest(controllers = AdminController.class, excludeAutoConfiguration = {
    org.springframework.boot.autoconfigure.aop.AopAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerQRCodeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminService adminService;

    @MockBean
    private SiteService siteService;

    @MockBean
    private WasenderQRServiceClient wasenderQRServiceClient;

    @MockBean
    private AdminAuthService adminAuthService;

    @MockBean
    private WasenderConfigService wasenderConfigService;

    @MockBean
    private UserWasenderService userWasenderService;

    @MockBean
    private com.clapgrow.notification.api.service.SendGridConfigService sendGridConfigService;

    @MockBean
    private com.clapgrow.notification.api.service.UserService userService;

    @MockBean
    private com.clapgrow.notification.api.service.WhatsAppSessionService whatsAppSessionService;

    private String testSessionId = "41276";
    private String testSessionName = "clapgrow-session";
    private String testPhoneNumber = "+1234567890";
    private String testApiKey = "test-api-key-12345";

    @BeforeEach
    void setUp() {
        when(wasenderConfigService.isConfigured()).thenReturn(true);
        when(userWasenderService.getApiKeyFromSession(any())).thenReturn(Optional.of(testApiKey));
    }

    @Test
    void testGetQRCode_WithSessionId_PrefersSessionId() throws Exception {
        // Arrange
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("success", true);
        mockResponse.put("qrCode", "2@testQRCodeString");
        mockResponse.put("sessionId", testSessionId);
        mockResponse.put("status", "NEED_SCAN");

        when(wasenderQRServiceClient.getQRCode(eq(testSessionId), anyString())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/admin/api/whatsapp/qrcode")
                .param("sessionId", testSessionId)
                .sessionAttr("wasenderApiKey", "test-api-key")
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.qrCode").exists())
                .andExpect(jsonPath("$.sessionId").value(testSessionId));

        // Verify service was called with session ID, not name
        verify(wasenderQRServiceClient).getQRCode(eq(testSessionId), anyString());
        verify(wasenderQRServiceClient, never()).getQRCode(eq(testSessionName), anyString());
    }

    @Test
    void testGetQRCode_WithSessionName_FallsBackToName() throws Exception {
        // Arrange
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("success", true);
        mockResponse.put("qrCode", "2@testQRCodeString");
        mockResponse.put("sessionName", testSessionName);

        when(wasenderQRServiceClient.getQRCode(eq(testSessionName), anyString())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/admin/api/whatsapp/qrcode")
                .param("sessionName", testSessionName)
                .sessionAttr("wasenderApiKey", "test-api-key")
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(wasenderQRServiceClient).getQRCode(eq(testSessionName), anyString());
    }

    @Test
    void testGetQRCode_WithBothSessionIdAndName_PrefersSessionId() throws Exception {
        // Arrange
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("success", true);
        mockResponse.put("qrCode", "2@testQRCodeString");
        mockResponse.put("sessionId", testSessionId);

        when(wasenderQRServiceClient.getQRCode(eq(testSessionId), anyString())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/admin/api/whatsapp/qrcode")
                .param("sessionId", testSessionId)
                .param("sessionName", testSessionName)
                .sessionAttr("wasenderApiKey", "test-api-key")
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(testSessionId));

        // Verify service was called with session ID (preferred)
        verify(wasenderQRServiceClient).getQRCode(eq(testSessionId), anyString());
        verify(wasenderQRServiceClient, never()).getQRCode(eq(testSessionName), anyString());
    }

    @Test
    void testGetQRCode_WithoutParameters_ReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/admin/api/whatsapp/qrcode")
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Either sessionName or sessionId must be provided"));

        verify(wasenderQRServiceClient, never()).getQRCode(anyString(), anyString());
    }

    @Test
    void testGetQRCode_WithEmptySessionId_UsesSessionName() throws Exception {
        // Arrange
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("success", true);
        mockResponse.put("qrCode", "2@testQRCodeString");

        when(wasenderQRServiceClient.getQRCode(eq(testSessionName), anyString())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/admin/api/whatsapp/qrcode")
                .param("sessionId", "")
                .param("sessionName", testSessionName)
                .sessionAttr("wasenderApiKey", "test-api-key")
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isOk());

        verify(wasenderQRServiceClient).getQRCode(eq(testSessionName), anyString());
    }

    @Test
    void testGetQRCode_WhenApiKeyNotConfigured_Returns428() throws Exception {
        // Arrange - Return empty Optional to simulate no API key in session
        when(userWasenderService.getApiKeyFromSession(any())).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/admin/api/whatsapp/qrcode")
                .param("sessionId", testSessionId)
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.requiresApiKey").value(true))
                .andExpect(jsonPath("$.error").value("WASender API key is not configured"));

        verify(wasenderQRServiceClient, never()).getQRCode(anyString(), anyString());
    }

    @Test
    void testCreateWhatsAppSession_WithRequiredFields_Success() throws Exception {
        // Arrange
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sessionName", testSessionName);
        requestBody.put("phoneNumber", testPhoneNumber);
        requestBody.put("accountProtection", true);
        requestBody.put("logMessages", true);

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("success", true);
        mockResponse.put("sessionName", testSessionName);
        mockResponse.put("sessionId", testSessionId);

        // Mock WhatsAppSession entity
        WhatsAppSession mockSession = new WhatsAppSession();
        mockSession.setId(UUID.randomUUID());
        mockSession.setSessionId(testSessionId);
        mockSession.setSessionName(testSessionName);
        mockSession.setPhoneNumber(testPhoneNumber);
        mockSession.setSessionApiKey(null); // Initially null, will be fetched later

        when(wasenderQRServiceClient.createSession(
                eq(testSessionName),
                eq(testPhoneNumber),
                eq(true),  // accountProtection
                eq(true),  // logMessages
                isNull(),  // webhookUrl
                isNull(),  // webhookEnabled
                isNull(),  // webhookEvents (String[])
                isNull(),  // readIncomingMessages
                isNull(),  // autoRejectCalls
                isNull(),  // ignoreGroups
                isNull(),  // ignoreChannels
                isNull(),  // ignoreBroadcasts
                anyString()  // apiKey
        )).thenReturn(mockResponse);

        when(whatsAppSessionService.saveSession(
                anyString(),  // sessionId
                anyString(),  // sessionName
                anyString(),  // phoneNumber
                any(Boolean.class),  // accountProtection
                any(Boolean.class),  // logMessages
                any(),  // webhookUrl
                any(),  // webhookEvents
                any()  // httpSession
        )).thenReturn(mockSession);

        // Mock getSessionDetails to return empty (so it doesn't try to fetch API key)
        Map<String, Object> emptySessionDetails = new HashMap<>();
        emptySessionDetails.put("success", false);
        when(wasenderQRServiceClient.getSessionDetails(anyString(), anyString())).thenReturn(emptySessionDetails);

        // Act & Assert
        mockMvc.perform(post("/admin/api/whatsapp/session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sessionId").value(testSessionId));

        verify(wasenderQRServiceClient).createSession(
                eq(testSessionName),
                eq(testPhoneNumber),
                eq(true),
                eq(true),
                isNull(),  // webhookUrl
                isNull(),  // webhookEnabled
                isNull(),  // webhookEvents
                isNull(),  // readIncomingMessages
                isNull(),  // autoRejectCalls
                isNull(),  // ignoreGroups
                isNull(),  // ignoreChannels
                isNull(),  // ignoreBroadcasts
                anyString()  // apiKey
        );
    }

    @Test
    void testCreateWhatsAppSession_WithoutPhoneNumber_ReturnsBadRequest() throws Exception {
        // Arrange
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sessionName", testSessionName);
        // phoneNumber is missing

        // Act & Assert
        mockMvc.perform(post("/admin/api/whatsapp/session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Phone number")));

        verify(wasenderQRServiceClient, never()).createSession(
                anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void testCreateWhatsAppSession_WithAccountProtectionAsBoolean() throws Exception {
        // Arrange
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sessionName", testSessionName);
        requestBody.put("phoneNumber", testPhoneNumber);
        requestBody.put("accountProtection", true); // Boolean, not string
        requestBody.put("logMessages", true);

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("success", true);

        // Mock WhatsAppSession entity
        WhatsAppSession mockSession = new WhatsAppSession();
        mockSession.setId(UUID.randomUUID());
        mockSession.setSessionId(testSessionId);
        mockSession.setSessionName(testSessionName);
        mockSession.setPhoneNumber(testPhoneNumber);
        mockSession.setSessionApiKey(null); // Initially null, will be fetched later

        when(wasenderQRServiceClient.createSession(
                anyString(),  // sessionName
                anyString(),  // phoneNumber
                any(Boolean.class),  // accountProtection
                any(Boolean.class),  // logMessages
                any(),  // webhookUrl
                any(),  // webhookEnabled
                any(),  // webhookEvents
                any(),  // readIncomingMessages
                any(),  // autoRejectCalls
                any(),  // ignoreGroups
                any(),  // ignoreChannels
                any(),  // ignoreBroadcasts
                anyString()  // apiKey
        )).thenReturn(mockResponse);

        when(whatsAppSessionService.saveSession(
                anyString(),  // sessionId
                anyString(),  // sessionName
                anyString(),  // phoneNumber
                any(Boolean.class),  // accountProtection
                any(Boolean.class),  // logMessages
                any(),  // webhookUrl
                any(),  // webhookEvents
                any()  // httpSession
        )).thenReturn(mockSession);

        // Mock getSessionDetails to return empty (so it doesn't try to fetch API key)
        Map<String, Object> emptySessionDetails = new HashMap<>();
        emptySessionDetails.put("success", false);
        when(wasenderQRServiceClient.getSessionDetails(anyString(), anyString())).thenReturn(emptySessionDetails);

        // Act & Assert
        mockMvc.perform(post("/admin/api/whatsapp/session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody))
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isOk());

        // Verify accountProtection was passed as Boolean
        verify(wasenderQRServiceClient).createSession(
                anyString(),  // sessionName
                anyString(),  // phoneNumber
                eq(true),     // accountProtection - Boolean value
                any(Boolean.class),  // logMessages
                any(),  // webhookUrl
                any(),  // webhookEnabled
                any(),  // webhookEvents
                any(),  // readIncomingMessages
                any(),  // autoRejectCalls
                any(),  // ignoreGroups
                any(),  // ignoreChannels
                any(),  // ignoreBroadcasts
                anyString()  // apiKey
        );
    }

    @Test
    void testConnectWhatsAppSession_WithSessionName_ParameterBinding() throws Exception {
        // Arrange
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("success", true);
        mockResponse.put("qrCode", "2@testQRCode");
        mockResponse.put("status", "NEED_SCAN");

        when(wasenderQRServiceClient.connectSession(eq(testSessionName), anyString())).thenReturn(mockResponse);

        // Act & Assert - This tests that @PathVariable with explicit name works
        mockMvc.perform(post("/admin/api/whatsapp/session/{sessionName}/connect", testSessionName)
                .sessionAttr("wasenderApiKey", "test-api-key")
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.qrCode").exists());

        verify(wasenderQRServiceClient).connectSession(eq(testSessionName), anyString());
    }

    @Test
    void testGetQRCode_Handles404Error_ReturnsErrorWithSessionId() throws Exception {
        // Arrange
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", "No query results for model [App\\Models\\WhatsappSession] clapgrow-session");
        errorResponse.put("statusCode", 404);
        errorResponse.put("statusText", "NOT_FOUND");

        when(wasenderQRServiceClient.getQRCode(eq(testSessionId), anyString())).thenReturn(errorResponse);

        // Act & Assert
        mockMvc.perform(get("/admin/api/whatsapp/qrcode")
                .param("sessionId", testSessionId)
                .sessionAttr("wasenderApiKey", "test-api-key")
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isOk()) // Controller returns 200, error is in response body
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.statusCode").value(404));

        verify(wasenderQRServiceClient).getQRCode(eq(testSessionId), anyString());
    }

    @Test
    void testGetQRCode_ResponseIncludesSessionId() throws Exception {
        // Arrange
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("success", true);
        mockResponse.put("qrCode", "2@testQRCodeString");
        mockResponse.put("sessionId", testSessionId); // Important: session ID is returned

        when(wasenderQRServiceClient.getQRCode(eq(testSessionId), anyString())).thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/admin/api/whatsapp/qrcode")
                .param("sessionId", testSessionId)
                .sessionAttr("wasenderApiKey", "test-api-key")
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(testSessionId))
                .andExpect(jsonPath("$.qrCode").exists());

        // This ensures frontend can extract and store session ID for future requests
    }

    @Test
    void testParameterBinding_ExplicitNameAttributes() throws Exception {
        // This test ensures that @RequestParam and @PathVariable have explicit name attributes
        // to prevent "Name for argument not specified" errors
        
        // Test sessionId parameter binding
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("success", true);
        when(wasenderQRServiceClient.getQRCode(eq(testSessionId), anyString())).thenReturn(mockResponse);

        mockMvc.perform(get("/admin/api/whatsapp/qrcode")
                .param("sessionId", testSessionId)
                .sessionAttr("wasenderApiKey", "test-api-key")
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isOk());

        // Test sessionName parameter binding
        when(wasenderQRServiceClient.getQRCode(eq(testSessionName), anyString())).thenReturn(mockResponse);
        mockMvc.perform(get("/admin/api/whatsapp/qrcode")
                .param("sessionName", testSessionName)
                .sessionAttr("wasenderApiKey", "test-api-key")
                .sessionAttr("userId", "test-user-id"))
                .andExpect(status().isOk());

        // If we get here without BAD_REQUEST, parameter binding is working
    }
}

