package com.ai.fabric.platform.backend.security.model;

import java.time.Instant;

public record PlatformUserSummary(
    String id,
    String email,
    String displayName,
    String role,
    String customerId,
    String customerName,
    String customerSlug,
    String status,
    Instant lastLoginAt,
    Instant createdAt,
    Instant updatedAt
) {
}
