package com.ai.fabric.platform.backend.partner.model;

import java.util.List;

public record PartnerSessionSummary(
    boolean authenticated,
    boolean signupRequired,
    PartnerAccountSummary account,
    PartnerMemberSummary member,
    long assignedStoreCount,
    long openEscalationCount,
    List<String> permissions
) {
}
