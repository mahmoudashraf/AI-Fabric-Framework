package com.easyluxury.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    public void logAdminAction(UUID adminId, String action, String details) {
        // TODO: Implement audit logging to database
        log.info("AUDIT: Admin {} performed action: {} - Details: {}", adminId, action, details);
    }

    public void logUserAction(UUID userId, String action, String details) {
        // TODO: Implement audit logging to database
        log.info("AUDIT: User {} performed action: {} - Details: {}", userId, action, details);
    }
}
