package com.ai.fabric.platform.backend.partner.model;

import java.util.List;

public record PartnerSupportThreadSummary(
    PartnerSupportEscalationSummary escalation,
    List<PartnerSupportReplySummary> replies
) {
}
