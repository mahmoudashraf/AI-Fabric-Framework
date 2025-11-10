package com.ai.infrastructure.compliance.policy;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Value object returned from {@link ComplianceCheckProvider}.
 */
public final class ComplianceCheckResult {

    private final boolean compliant;
    private final List<String> violations;
    private final String details;
    private final LocalDateTime timestamp;

    private ComplianceCheckResult(Builder builder) {
        this.compliant = builder.compliant;
        this.violations = builder.violations == null
            ? List.of()
            : List.copyOf(builder.violations);
        this.details = builder.details;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
    }

    public boolean isCompliant() {
        return compliant;
    }

    public List<String> getViolations() {
        return violations;
    }

    public String getDetails() {
        return details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean compliant = true;
        private List<String> violations = Collections.emptyList();
        private String details;
        private LocalDateTime timestamp;

        public Builder compliant(boolean compliant) {
            this.compliant = compliant;
            return this;
        }

        public Builder violations(List<String> violations) {
            this.violations = violations;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ComplianceCheckResult build() {
            Objects.requireNonNull(violations, "violations");
            return new ComplianceCheckResult(this);
        }
    }
}
