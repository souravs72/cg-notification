package com.clapgrow.notification.api.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdminAuthServiceTest {
    
    @Test
    void testValidateAdminKeyWithConfiguredKey() {
        AdminAuthService service = new AdminAuthService("test-admin-key-123");
        assertDoesNotThrow(() -> service.validateAdminKey("test-admin-key-123"));
    }
    
    @Test
    void testValidateAdminKeyWithWrongKey() {
        AdminAuthService service = new AdminAuthService("test-admin-key-123");
        assertThrows(SecurityException.class, () -> service.validateAdminKey("wrong-key"));
    }
    
    @Test
    void testValidateAdminKeyWithEmptyKey() {
        AdminAuthService service = new AdminAuthService("test-admin-key-123");
        assertThrows(SecurityException.class, () -> service.validateAdminKey(""));
        assertThrows(SecurityException.class, () -> service.validateAdminKey(null));
    }
    
    @Test
    void testValidateAdminKeyWithUnconfiguredService() {
        AdminAuthService service = new AdminAuthService("");
        assertThrows(SecurityException.class, () -> service.validateAdminKey("any-key"));
    }
}

