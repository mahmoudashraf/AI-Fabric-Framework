package com.ai.infrastructure.compliance;

import com.ai.infrastructure.audit.AuditService;
import com.ai.infrastructure.compliance.policy.ComplianceCheckProvider;
import com.ai.infrastructure.compliance.policy.ComplianceCheckResult;
import com.ai.infrastructure.dto.AIAuditLog;
import com.ai.infrastructure.dto.AIComplianceReport;
import com.ai.infrastructure.dto.AIComplianceRequest;
import com.ai.infrastructure.dto.AIComplianceResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Infrastructure-only compliance service delegating policy decisions to customer hooks.
 */
@Slf4j
@RequiredArgsConstructor
public class AIComplianceService {

    private final AuditService auditService;
    private final Clock clock;

    private final Map<String, List<AIComplianceReport>> complianceReports = new ConcurrentHashMap<>();
    private final Map<String, List<AIAuditLog>> auditLogIndex = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private ComplianceCheckProvider complianceProvider;

    public AIComplianceResponse checkCompliance(AIComplianceRequest request) {
        Objects.requireNonNull(request, "compliance request must not be null");
        LocalDateTime timestamp = Optional.ofNullable(request.getTimestamp())
            .orElseGet(() -> LocalDateTime.now(clock));

        boolean compliant = true;
        List<String> violations = new ArrayList<>();
        String details = null;

        if (complianceProvider != null) {
            try {
                ComplianceCheckResult result = complianceProvider.checkCompliance(request);
                if (result != null) {
                    compliant = result.isCompliant();
                    if (result.getViolations() != null) {
                        violations.addAll(result.getViolations());
                    }
                    details = result.getDetails();
                }
            } catch (Exception hookEx) {
                log.warn("ComplianceCheckProvider threw an exception: {}", hookEx.getMessage());
                compliant = false;
                violations.add("COMPLIANCE_PROVIDER_ERROR");
            }
        }

        recordComplianceOutcome(request, compliant, violations, timestamp, details);

        auditService.logOperation(
            request.getRequestId(),
            request.getUserId(),
            compliant ? "COMPLIANCE_PASS" : "COMPLIANCE_FAIL",
            violations);

        return AIComplianceResponse.builder()
            .requestId(request.getRequestId())
            .userId(request.getUserId())
            .overallCompliant(compliant)
            .violations(List.copyOf(violations))
            .processingTimeMs(0L)
            .timestamp(timestamp)
            .success(true)
            .report(buildReport(request, compliant, violations, timestamp, details))
            .build();
    }

    public List<AIComplianceReport> getComplianceReports(String userId) {
        return complianceReports.containsKey(userId)
            ? List.copyOf(complianceReports.get(userId))
            : List.of();
    }

    public AIComplianceReport generateSummaryReport(String userId, String period) {
        List<AIComplianceReport> reports = complianceReports.getOrDefault(userId, List.of());
        double averageScore = reports.stream()
            .mapToDouble(report -> Boolean.TRUE.equals(report.isOverallCompliant()) ? 100.0 : 0.0)
            .average()
            .orElse(100.0);

        return AIComplianceReport.builder()
            .reportId("SUMMARY_" + LocalDateTime.now(clock).toString())
            .userId(userId)
            .timestamp(LocalDateTime.now(clock))
            .overallCompliant(averageScore >= 75.0)
            .complianceScore(averageScore)
            .violations(Collections.emptyList())
            .build();
    }

    public Map<String, Object> getComplianceStatistics() {
        long totalReports = complianceReports.values().stream()
            .mapToLong(List::size)
            .sum();
        long compliantReports = complianceReports.values().stream()
            .flatMap(List::stream)
            .filter(AIComplianceReport::isOverallCompliant)
            .count();

        return Map.of(
            "totalReports", totalReports,
            "compliantReports", compliantReports,
            "complianceRate", totalReports > 0 ? (double) compliantReports / totalReports : 0.0,
            "uniqueUsers", complianceReports.size()
        );
    }

    private void recordComplianceOutcome(AIComplianceRequest request,
                                         boolean compliant,
                                         List<String> violations,
                                         LocalDateTime timestamp,
                                         String details) {
        AIComplianceReport report = buildReport(request, compliant, violations, timestamp, details);
        complianceReports
            .computeIfAbsent(request.getUserId(), key -> Collections.synchronizedList(new ArrayList<>()))
            .add(report);

        AIAuditLog auditLog = AIAuditLog.builder()
            .logId("AUDIT_" + timestamp.toEpochSecond(clock.getZone().getRules().getOffset(timestamp)))
            .requestId(request.getRequestId())
            .userId(request.getUserId())
            .timestamp(timestamp)
            .operationType(compliant ? "COMPLIANT" : "VIOLATION")
            .violations(List.copyOf(violations))
            .details(details)
            .build();

        auditLogIndex
            .computeIfAbsent(request.getUserId(), key -> Collections.synchronizedList(new ArrayList<>()))
            .add(auditLog);
    }

    private AIComplianceReport buildReport(AIComplianceRequest request,
                                           boolean compliant,
                                           List<String> violations,
                                           LocalDateTime timestamp,
                                           String details) {
        return AIComplianceReport.builder()
            .reportId("COMP_" + timestamp.toString())
            .requestId(request.getRequestId())
            .userId(request.getUserId())
            .timestamp(timestamp)
            .overallCompliant(compliant)
            .violations(List.copyOf(violations))
            .dataClassification(request.getDataClassification())
            .purpose(request.getPurpose())
            .regulationTypes(request.getRegulationTypes())
            .notes(details)
            .build();
    }
}
