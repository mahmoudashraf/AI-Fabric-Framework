export interface AuditLog { id: string; timestamp: string; userId: string; action: string; resource: string; result: string; details: string; ipAddress: string; userAgent: string; sessionId: string; riskLevel: string; hasAnomalies: boolean; violations: string[]; regulationTypes: string[]; }
export interface AuditPolicy { id: string; name: string; description: string; rules: string[]; isActive: boolean; createdAt: string; updatedAt: string; }
export interface AuditReport { id: string; name: string; description: string; generatedAt: string; period: { start: string; end: string; }; summary: { totalLogs: number; highRiskLogs: number; violations: number; anomalies: number; }; findings: string[]; recommendations: string[]; }
export interface AuditMetrics { totalLogs: number; highRiskLogs: number; violations: number; anomalies: number; lastUpdated: string; anomalyRate: number; averageResponseTime: number; auditTrend: "increasing" | "decreasing" | "stable"; anomalyTrend: "increasing" | "decreasing" | "stable"; }
export interface AuditSearchRequest { query: string; filters: Record<string, any>; limit: number; offset: number; }
export interface AuditSearchResponse { logs: AuditLog[]; totalCount: number; hasMore: boolean; }
export interface AuditConfiguration { retentionDays: number; logLevel: string; realTimeMonitoring: boolean; alertThresholds: Record<string, number>; }
export interface ComplianceViolation { id: string; type: string; severity: string; description: string; detectedAt: string; resolvedAt?: string; status: "ACTIVE" | "RESOLVED" | "IGNORED"; }
export interface ComplianceCheckRequest { operationType: string; data: any; context?: Record<string, any>; }
export interface ComplianceCheckResponse { compliant: boolean; violations: ComplianceViolation[]; score: number; recommendations: string[]; }
export interface ComplianceMetrics { totalReports: number; compliantReports: number; violations: number; lastUpdated: string; complianceScore: number; violationTrend: "stable" | "improving" | "declining"; complianceTrend: "stable" | "improving" | "declining"; }
export interface ComplianceFramework { id: string; name: string; version: string; rules: string[]; }
export interface ComplianceRequirement { id: string; name: string; description: string; category: string; mandatory: boolean; }
export interface ComplianceReport { id: string; name: string; description: string; generatedAt: string; period: { start: string; end: string; }; summary: { totalLogs: number; highRiskLogs: number; violations: number; anomalies: number; }; findings: string[]; recommendations: string[]; }
