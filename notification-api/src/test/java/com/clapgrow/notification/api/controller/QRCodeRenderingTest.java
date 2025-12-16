package com.clapgrow.notification.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for QR code rendering functionality.
 * Verifies that QR code endpoints return proper data that can be rendered.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QRCodeRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testQRCodeEndpointReturnsValidData() throws Exception {
        // This test verifies the endpoint structure
        // Actual QR code rendering is tested in frontend
        // Note: This requires a valid session to be created first
        
        // Test that the endpoint exists and returns proper JSON structure
        // We expect either success with qrCode field or proper error response
        mockMvc.perform(get("/admin/api/whatsapp/qrcode")
                .param("sessionId", "test-session-id"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$").exists());
    }

    @Test
    void testQRCodeResponseStructure() throws Exception {
        // Verify response structure contains expected fields
        mockMvc.perform(get("/admin/api/whatsapp/qrcode")
                .param("sessionId", "test-session-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.success").isBoolean());
    }

    @Test
    void testQRCodeStringFormat() throws Exception {
        // When QR code is returned, verify it's a non-empty string
        // This ensures the QR code data is in the correct format for rendering
        mockMvc.perform(get("/admin/api/whatsapp/qrcode")
                .param("sessionId", "test-session-id"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String content = result.getResponse().getContentAsString();
                    // Verify response is valid JSON
                    assert content != null && !content.isEmpty();
                    // If success is true, qrCode should be present
                    if (content.contains("\"success\":true")) {
                        assert content.contains("qrCode") : "Response should contain qrCode field when successful";
                    }
                });
    }
}












