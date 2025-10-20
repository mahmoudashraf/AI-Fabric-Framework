import { useState, useEffect, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { 
  AuditLog, 
  AuditMetrics, 
  AuditSearchRequest, 
  AuditSearchResponse,
  AuditConfiguration,
  AuditPolicy,
  AuditReport
} from '../types/ai';

interface UseAIAuditOptions {
  refreshInterval?: number;
  autoRefresh?: boolean;
  onAnomalyDetected?: (log: AuditLog) => void;
  onReportGenerated?: (report: AuditReport) => void;
}

interface UseAIAuditReturn {
  // Data
  logs: AuditLog[];
  metrics: AuditMetrics | null;
  configuration: AuditConfiguration | null;
  policies: AuditPolicy[];
  reports: AuditReport[];
  
  // Loading states
  isLoading: boolean;
  isLogsLoading: boolean;
  isMetricsLoading: boolean;
  isConfigurationLoading: boolean;
  isPoliciesLoading: boolean;
  isReportsLoading: boolean;
  
  // Error states
  error: string | null;
  logsError: string | null;
  metricsError: string | null;
  configurationError: string | null;
  policiesError: string | null;
  reportsError: string | null;
  
  // Actions
  searchLogs: (request: AuditSearchRequest) => Promise<AuditSearchResponse>;
  createPolicy: (policy: Omit<AuditPolicy, 'id' | 'createdAt' | 'updatedAt'>) => Promise<AuditPolicy>;
  updatePolicy: (id: string, updates: Partial<AuditPolicy>) => Promise<AuditPolicy>;
  deletePolicy: (id: string) => Promise<void>;
  updateConfiguration: (config: Partial<AuditConfiguration>) => Promise<AuditConfiguration>;
  generateReport: (startDate: string, endDate: string, reportType: string) => Promise<AuditReport>;
  exportLogs: (filters: Record<string, any>) => Promise<Blob>;
  
  // Utilities
  refresh: () => void;
  refreshLogs: () => void;
  refreshMetrics: () => void;
  refreshConfiguration: () => void;
  refreshPolicies: () => void;
  refreshReports: () => void;
  
  // Computed values
  anomalyLogs: AuditLog[];
  highRiskLogs: AuditLog[];
  recentLogs: AuditLog[];
  logCount: number;
  policyCount: number;
  reportCount: number;
  anomalyRate: number;
  averageResponseTime: number;
  auditTrend: 'increasing' | 'decreasing' | 'stable';
  anomalyTrend: 'increasing' | 'decreasing' | 'stable';
}

const useAIAudit = (options: UseAIAuditOptions = {}): UseAIAuditReturn => {
  const {
    refreshInterval = 30000,
    autoRefresh = true,
    onAnomalyDetected,
    onReportGenerated,
  } = options;

  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  // Query keys
  const queryKeys = {
    logs: ['ai-audit', 'logs'],
    metrics: ['ai-audit', 'metrics'],
    configuration: ['ai-audit', 'configuration'],
    policies: ['ai-audit', 'policies'],
    reports: ['ai-audit', 'reports'],
  };

  // Fetch logs
  const {
    data: logs = [],
    isLoading: isLogsLoading,
    error: logsError,
    refetch: refetchLogs,
  } = useQuery({
    queryKey: queryKeys.logs,
    queryFn: async (): Promise<AuditLog[]> => {
      const response = await fetch('/api/ai/audit/logs');
      if (!response.ok) {
        throw new Error('Failed to fetch audit logs');
      }
      return response.json();
    },
    refetchInterval: autoRefresh ? refreshInterval : false,
    refetchIntervalInBackground: false,
  });

  // Fetch metrics
  const {
    data: metrics,
    isLoading: isMetricsLoading,
    error: metricsError,
    refetch: refetchMetrics,
  } = useQuery({
    queryKey: queryKeys.metrics,
    queryFn: async (): Promise<AuditMetrics> => {
      const response = await fetch('/api/ai/audit/metrics');
      if (!response.ok) {
        throw new Error('Failed to fetch audit metrics');
      }
      return response.json();
    },
    refetchInterval: autoRefresh ? refreshInterval : false,
    refetchIntervalInBackground: false,
  });

  // Fetch configuration
  const {
    data: configuration,
    isLoading: isConfigurationLoading,
    error: configurationError,
    refetch: refetchConfiguration,
  } = useQuery({
    queryKey: queryKeys.configuration,
    queryFn: async (): Promise<AuditConfiguration> => {
      const response = await fetch('/api/ai/audit/configuration');
      if (!response.ok) {
        throw new Error('Failed to fetch audit configuration');
      }
      return response.json();
    },
  });

  // Fetch policies
  const {
    data: policies = [],
    isLoading: isPoliciesLoading,
    error: policiesError,
    refetch: refetchPolicies,
  } = useQuery({
    queryKey: queryKeys.policies,
    queryFn: async (): Promise<AuditPolicy[]> => {
      const response = await fetch('/api/ai/audit/policies');
      if (!response.ok) {
        throw new Error('Failed to fetch audit policies');
      }
      return response.json();
    },
  });

  // Fetch reports
  const {
    data: reports = [],
    isLoading: isReportsLoading,
    error: reportsError,
    refetch: refetchReports,
  } = useQuery({
    queryKey: queryKeys.reports,
    queryFn: async (): Promise<AuditReport[]> => {
      const response = await fetch('/api/ai/audit/reports');
      if (!response.ok) {
        throw new Error('Failed to fetch audit reports');
      }
      return response.json();
    },
  });

  // Search logs mutation
  const searchLogsMutation = useMutation({
    mutationFn: async (request: AuditSearchRequest): Promise<AuditSearchResponse> => {
      const response = await fetch('/api/ai/audit/search', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      });
      
      if (!response.ok) {
        throw new Error('Failed to search audit logs');
      }
      
      const result = await response.json();
      
      // Trigger callback if anomalies detected
      if (result.logs && result.logs.length > 0) {
        result.logs.forEach((log: AuditLog) => {
          if (log.hasAnomalies) {
            onAnomalyDetected?.(log);
          }
        });
      }
      
      return result;
    },
  });

  // Create policy mutation
  const createPolicyMutation = useMutation({
    mutationFn: async (policy: Omit<AuditPolicy, 'id' | 'createdAt' | 'updatedAt'>): Promise<AuditPolicy> => {
      const response = await fetch('/api/ai/audit/policies', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(policy),
      });
      
      if (!response.ok) {
        throw new Error('Failed to create audit policy');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.policies });
    },
  });

  // Update policy mutation
  const updatePolicyMutation = useMutation({
    mutationFn: async ({ id, updates }: { id: string; updates: Partial<AuditPolicy> }): Promise<AuditPolicy> => {
      const response = await fetch(`/api/ai/audit/policies/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(updates),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update audit policy');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.policies });
    },
  });

  // Delete policy mutation
  const deletePolicyMutation = useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const response = await fetch(`/api/ai/audit/policies/${id}`, {
        method: 'DELETE',
      });
      
      if (!response.ok) {
        throw new Error('Failed to delete audit policy');
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.policies });
    },
  });

  // Update configuration mutation
  const updateConfigurationMutation = useMutation({
    mutationFn: async (config: Partial<AuditConfiguration>): Promise<AuditConfiguration> => {
      const response = await fetch('/api/ai/audit/configuration', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(config),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update audit configuration');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.configuration });
    },
  });

  // Generate report mutation
  const generateReportMutation = useMutation({
    mutationFn: async ({ startDate, endDate, reportType }: { startDate: string; endDate: string; reportType: string }): Promise<AuditReport> => {
      const response = await fetch('/api/ai/audit/reports/generate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ startDate, endDate, reportType }),
      });
      
      if (!response.ok) {
        throw new Error('Failed to generate audit report');
      }
      
      const result = await response.json();
      onReportGenerated?.(result);
      return result;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.reports });
    },
  });

  // Export logs mutation
  const exportLogsMutation = useMutation({
    mutationFn: async (filters: Record<string, any>): Promise<Blob> => {
      const response = await fetch('/api/ai/audit/export', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(filters),
      });
      
      if (!response.ok) {
        throw new Error('Failed to export audit logs');
      }
      
      return response.blob();
    },
  });

  // Action functions
  const searchLogs = useCallback(
    (request: AuditSearchRequest) => searchLogsMutation.mutateAsync(request),
    [searchLogsMutation]
  );

  const createPolicy = useCallback(
    (policy: Omit<AuditPolicy, 'id' | 'createdAt' | 'updatedAt'>) => 
      createPolicyMutation.mutateAsync(policy),
    [createPolicyMutation]
  );

  const updatePolicy = useCallback(
    (id: string, updates: Partial<AuditPolicy>) => 
      updatePolicyMutation.mutateAsync({ id, updates }),
    [updatePolicyMutation]
  );

  const deletePolicy = useCallback(
    (id: string) => deletePolicyMutation.mutateAsync(id),
    [deletePolicyMutation]
  );

  const updateConfiguration = useCallback(
    (config: Partial<AuditConfiguration>) => 
      updateConfigurationMutation.mutateAsync(config),
    [updateConfigurationMutation]
  );

  const generateReport = useCallback(
    (startDate: string, endDate: string, reportType: string) => 
      generateReportMutation.mutateAsync({ startDate, endDate, reportType }),
    [generateReportMutation]
  );

  const exportLogs = useCallback(
    (filters: Record<string, any>) => exportLogsMutation.mutateAsync(filters),
    [exportLogsMutation]
  );

  // Refresh functions
  const refresh = useCallback(() => {
    refetchLogs();
    refetchMetrics();
    refetchConfiguration();
    refetchPolicies();
    refetchReports();
  }, [refetchLogs, refetchMetrics, refetchConfiguration, refetchPolicies, refetchReports]);

  const refreshLogs = useCallback(() => refetchLogs(), [refetchLogs]);
  const refreshMetrics = useCallback(() => refetchMetrics(), [refetchMetrics]);
  const refreshConfiguration = useCallback(() => refetchConfiguration(), [refetchConfiguration]);
  const refreshPolicies = useCallback(() => refetchPolicies(), [refetchPolicies]);
  const refreshReports = useCallback(() => refetchReports(), [refetchReports]);

  // Computed values
  const anomalyLogs = logs.filter(log => log.hasAnomalies);
  const highRiskLogs = logs.filter(log => log.riskLevel === 'HIGH' || log.riskLevel === 'CRITICAL');
  const recentLogs = logs
    .sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
    .slice(0, 10);
  const logCount = logs.length;
  const policyCount = policies.length;
  const reportCount = reports.length;
  const anomalyRate = metrics?.anomalyRate || 0;
  const averageResponseTime = metrics?.averageResponseTime || 0;
  const auditTrend = metrics?.auditTrend || 'stable';
  const anomalyTrend = metrics?.anomalyTrend || 'stable';

  // Overall loading state
  const isLoading = isLogsLoading || isMetricsLoading || isConfigurationLoading || isPoliciesLoading || isReportsLoading;

  // Overall error state
  const overallError = error || logsError || metricsError || configurationError || policiesError || reportsError;

  return {
    // Data
    logs,
    metrics,
    configuration,
    policies,
    reports,
    
    // Loading states
    isLoading,
    isLogsLoading,
    isMetricsLoading,
    isConfigurationLoading,
    isPoliciesLoading,
    isReportsLoading,
    
    // Error states
    error: overallError,
    logsError,
    metricsError,
    configurationError,
    policiesError,
    reportsError,
    
    // Actions
    searchLogs,
    createPolicy,
    updatePolicy,
    deletePolicy,
    updateConfiguration,
    generateReport,
    exportLogs,
    
    // Utilities
    refresh,
    refreshLogs,
    refreshMetrics,
    refreshConfiguration,
    refreshPolicies,
    refreshReports,
    
    // Computed values
    anomalyLogs,
    highRiskLogs,
    recentLogs,
    logCount,
    policyCount,
    reportCount,
    anomalyRate,
    averageResponseTime,
    auditTrend,
    anomalyTrend,
  };
};

export default useAIAudit;
export { useAIAudit };