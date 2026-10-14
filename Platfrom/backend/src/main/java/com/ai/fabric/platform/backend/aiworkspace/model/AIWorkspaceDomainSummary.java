package com.ai.fabric.platform.backend.aiworkspace.model;

import java.time.Instant;

public record AIWorkspaceDomainSummary(
    String hostname,
    String url,
    String type,
    String status,
    String dnsRecordName,
    String dnsRecordValue,
    String routingRecordName,
    String routingRecordValue,
    String lastVerificationError,
    Instant verifiedAt
) {
}
