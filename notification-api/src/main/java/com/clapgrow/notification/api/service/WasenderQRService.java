package com.clapgrow.notification.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WasenderQRService {
    
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    
    @Value("${wasender.api.base-url:https://wasenderapi.com/api}")
    private String wasenderBaseUrl;
    
    @Value("${wasender.api.key:}")
    private String wasenderApiKey;

    public Map<String, Object> getQRCode(String sessionName) {
        try {
            WebClient webClient = webClientBuilder.build();
            
            String qrCodeUrl = wasenderBaseUrl + "/whatsapp-sessions/" + sessionName + "/qrcode";
            
            String qrCodeResponse = webClient.get()
                .uri(qrCodeUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + wasenderApiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            // Try to parse QR code from response (could be JSON with qrCode field or direct image data)
            String qrCodeData = qrCodeResponse;
            try {
                JsonNode jsonNode = objectMapper.readTree(qrCodeResponse);
                if (jsonNode.has("qrCode")) {
                    qrCodeData = jsonNode.get("qrCode").asText();
                } else if (jsonNode.has("data")) {
                    qrCodeData = jsonNode.get("data").asText();
                }
            } catch (Exception e) {
                // If not JSON, assume it's already the QR code data
                log.debug("QR code response is not JSON, using as-is");
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("qrCode", qrCodeData);
            response.put("sessionName", sessionName);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error fetching QR code from WASender", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    public Map<String, Object> createSession(String sessionName) {
        try {
            WebClient webClient = webClientBuilder.build();
            
            String createUrl = wasenderBaseUrl + "/whatsapp-sessions";
            
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("name", sessionName);
            
            String response = webClient.post()
                .uri(createUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + wasenderApiKey)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("response", response);
            result.put("sessionName", sessionName);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error creating WASender session", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }
}
