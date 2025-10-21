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
export interface ComplianceMetrics { totalReports: number; compliantReports: number; violations: number; lastUpdated: string; complianceScore: number; violationTrend: "increasing" | "decreasing" | "stable"; complianceTrend: "increasing" | "decreasing" | "stable"; }
export interface ComplianceFramework { id: string; name: string; version: string; rules: string[]; }
export interface ComplianceRequirement { id: string; name: string; description: string; category: string; mandatory: boolean; }
export interface ComplianceReport { id: string; name: string; description: string; generatedAt: string; period: { start: string; end: string; }; summary: { totalLogs: number; highRiskLogs: number; violations: number; anomalies: number; }; findings: string[]; recommendations: string[]; }
export interface DataSubject { id: string; name: string; email: string; dataTypes: string[]; consentGiven: boolean; lastUpdated: string; }
export interface PrivacyViolation { id: string; type: string; severity: string; description: string; detectedAt: string; resolvedAt?: string; status: "ACTIVE" | "RESOLVED" | "IGNORED"; }
export interface DataClassification { id: string; name: string; level: string; description: string; retentionPeriod: number; }
export interface PrivacySettings { consentRequired: boolean; dataRetentionDays: number; anonymizationEnabled: boolean; encryptionEnabled: boolean; }
export interface PrivacyMetrics { totalSubjects: number; violations: number; consentRate: number; lastUpdated: string; complianceScore: number; violationTrend: "increasing" | "decreasing" | "stable"; complianceTrend: "increasing" | "decreasing" | "stable"; }
export interface ConsentRequest { subjectId: string; dataTypes: string[]; purpose: string; }
export interface ConsentResponse { consentId: string; granted: boolean; expiresAt?: string; }
export interface DataRetentionPolicy { id: string; name: string; dataType: string; retentionPeriod: number; autoDelete: boolean; }
export interface PrivacyAudit { id: string; subjectId: string; action: string; timestamp: string; result: string; }
export interface SecurityThreat { id: string; type: string; severity: string; description: string; detectedAt: string; resolvedAt?: string; }
export interface ThreatDetectionRequest { data: any; context?: Record<string, any>; }
export interface ThreatDetectionResponse { threats: SecurityThreat[]; score: number; recommendations: string[]; }
export interface SecurityMetrics { totalThreats: number; blockedRequests: number; securityScore: number; lastUpdated: string; averageResponseTime: number; threatTrend: "increasing" | "decreasing" | "stable"; securityTrend: "increasing" | "decreasing" | "stable"; incidentTrend: "increasing" | "decreasing" | "stable"; }
export interface SecurityIncident { id: string; type: string; severity: string; description: string; detectedAt: string; resolvedAt?: string; status: "ACTIVE" | "RESOLVED" | "IGNORED"; }
export interface SecurityPolicy { id: string; name: string; description: string; rules: string[]; isActive: boolean; }
export interface SecurityConfiguration { threatDetectionEnabled: boolean; realTimeMonitoring: boolean; alertThresholds: Record<string, number>; }
export interface SearchRequest { query: string; filters?: Record<string, any>; limit?: number; offset?: number; }
export interface SearchResponse { results: Document[]; totalCount: number; hasMore: boolean; }
export interface Document { id: string; content: string; title: string; type: string; score: number; metadata: Record<string, any>; }
export interface QueryExpansionRequest { query: string; context?: Record<string, any>; }
export interface QueryExpansionResponse { expandedQuery: string; synonyms: string[]; context: Record<string, any>; }
export interface RAGConfiguration { model: string; temperature: number; maxTokens: number; topK: number; }
export interface RAGMetrics { totalQueries: number; averageResponseTime: number; accuracy: number; lastUpdated: string; searchAccuracy: number; queryExpansionRate: number; documentRetrievalRate: number; searchTrend: "increasing" | "decreasing" | "stable"; }
