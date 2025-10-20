import { useState, useEffect, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { 
  ComplianceViolation, 
  ComplianceMetrics, 
  ComplianceCheckRequest, 
  ComplianceCheckResponse,
  ComplianceFramework,
  ComplianceRequirement,
  ComplianceReport
} from '../types/ai';

interface UseAIComplianceOptions {
  refreshInterval?: number;
  autoRefresh?: boolean;
  onViolationDetected?: (violation: ComplianceViolation) => void;
  onReportGenerated?: (report: ComplianceReport) => void;
}

interface UseAIComplianceReturn {
  // Data
  violations: ComplianceViolation[];
  metrics: ComplianceMetrics | null;
  frameworks: ComplianceFramework[];
  requirements: ComplianceRequirement[];
  reports: ComplianceReport[];
  
  // Loading states
  isLoading: boolean;
  isViolationsLoading: boolean;
  isMetricsLoading: boolean;
  isFrameworksLoading: boolean;
  isRequirementsLoading: boolean;
  isReportsLoading: boolean;
  
  // Error states
  error: string | null;
  violationsError: string | null;
  metricsError: string | null;
  frameworksError: string | null;
  requirementsError: string | null;
  reportsError: string | null;
  
  // Actions
  checkCompliance: (request: ComplianceCheckRequest) => Promise<ComplianceCheckResponse>;
  createViolation: (violation: Omit<ComplianceViolation, 'id' | 'createdAt' | 'updatedAt'>) => Promise<ComplianceViolation>;
  updateViolation: (id: string, updates: Partial<ComplianceViolation>) => Promise<ComplianceViolation>;
  resolveViolation: (id: string) => Promise<void>;
  createFramework: (framework: Omit<ComplianceFramework, 'id' | 'createdAt' | 'updatedAt'>) => Promise<ComplianceFramework>;
  updateFramework: (id: string, updates: Partial<ComplianceFramework>) => Promise<ComplianceFramework>;
  deleteFramework: (id: string) => Promise<void>;
  createRequirement: (requirement: Omit<ComplianceRequirement, 'id' | 'createdAt' | 'updatedAt'>) => Promise<ComplianceRequirement>;
  updateRequirement: (id: string, updates: Partial<ComplianceRequirement>) => Promise<ComplianceRequirement>;
  deleteRequirement: (id: string) => Promise<void>;
  generateReport: (frameworkId: string, startDate: string, endDate: string) => Promise<ComplianceReport>;
  
  // Utilities
  refresh: () => void;
  refreshViolations: () => void;
  refreshMetrics: () => void;
  refreshFrameworks: () => void;
  refreshRequirements: () => void;
  refreshReports: () => void;
  
  // Computed values
  criticalViolations: ComplianceViolation[];
  highViolations: ComplianceViolation[];
  activeViolations: ComplianceViolation[];
  resolvedViolations: ComplianceViolation[];
  violationCount: number;
  frameworkCount: number;
  requirementCount: number;
  reportCount: number;
  complianceScore: number;
  violationTrend: 'increasing' | 'decreasing' | 'stable';
  complianceTrend: 'improving' | 'declining' | 'stable';
}

const useAICompliance = (options: UseAIComplianceOptions = {}): UseAIComplianceReturn => {
  const {
    refreshInterval = 30000,
    autoRefresh = true,
    onViolationDetected,
    onReportGenerated,
  } = options;

  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  // Query keys
  const queryKeys = {
    violations: ['ai-compliance', 'violations'],
    metrics: ['ai-compliance', 'metrics'],
    frameworks: ['ai-compliance', 'frameworks'],
    requirements: ['ai-compliance', 'requirements'],
    reports: ['ai-compliance', 'reports'],
  };

  // Fetch violations
  const {
    data: violations = [],
    isLoading: isViolationsLoading,
    error: violationsError,
    refetch: refetchViolations,
  } = useQuery({
    queryKey: queryKeys.violations,
    queryFn: async (): Promise<ComplianceViolation[]> => {
      const response = await fetch('/api/ai/compliance/violations');
      if (!response.ok) {
        throw new Error('Failed to fetch violations');
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
    queryFn: async (): Promise<ComplianceMetrics> => {
      const response = await fetch('/api/ai/compliance/metrics');
      if (!response.ok) {
        throw new Error('Failed to fetch metrics');
      }
      return response.json();
    },
    refetchInterval: autoRefresh ? refreshInterval : false,
    refetchIntervalInBackground: false,
  });

  // Fetch frameworks
  const {
    data: frameworks = [],
    isLoading: isFrameworksLoading,
    error: frameworksError,
    refetch: refetchFrameworks,
  } = useQuery({
    queryKey: queryKeys.frameworks,
    queryFn: async (): Promise<ComplianceFramework[]> => {
      const response = await fetch('/api/ai/compliance/frameworks');
      if (!response.ok) {
        throw new Error('Failed to fetch frameworks');
      }
      return response.json();
    },
  });

  // Fetch requirements
  const {
    data: requirements = [],
    isLoading: isRequirementsLoading,
    error: requirementsError,
    refetch: refetchRequirements,
  } = useQuery({
    queryKey: queryKeys.requirements,
    queryFn: async (): Promise<ComplianceRequirement[]> => {
      const response = await fetch('/api/ai/compliance/requirements');
      if (!response.ok) {
        throw new Error('Failed to fetch requirements');
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
    queryFn: async (): Promise<ComplianceReport[]> => {
      const response = await fetch('/api/ai/compliance/reports');
      if (!response.ok) {
        throw new Error('Failed to fetch reports');
      }
      return response.json();
    },
  });

  // Check compliance mutation
  const checkComplianceMutation = useMutation({
    mutationFn: async (request: ComplianceCheckRequest): Promise<ComplianceCheckResponse> => {
      const response = await fetch('/api/ai/compliance/check', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      });
      
      if (!response.ok) {
        throw new Error('Failed to check compliance');
      }
      
      const result = await response.json();
      
      // Trigger callback if violations detected
      if (result.violations && result.violations.length > 0) {
        result.violations.forEach((violation: ComplianceViolation) => {
          onViolationDetected?.(violation);
        });
      }
      
      return result;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.violations });
      queryClient.invalidateQueries({ queryKey: queryKeys.metrics });
    },
  });

  // Create violation mutation
  const createViolationMutation = useMutation({
    mutationFn: async (violation: Omit<ComplianceViolation, 'id' | 'createdAt' | 'updatedAt'>): Promise<ComplianceViolation> => {
      const response = await fetch('/api/ai/compliance/violations', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(violation),
      });
      
      if (!response.ok) {
        throw new Error('Failed to create violation');
      }
      
      const result = await response.json();
      onViolationDetected?.(result);
      return result;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.violations });
    },
  });

  // Update violation mutation
  const updateViolationMutation = useMutation({
    mutationFn: async ({ id, updates }: { id: string; updates: Partial<ComplianceViolation> }): Promise<ComplianceViolation> => {
      const response = await fetch(`/api/ai/compliance/violations/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(updates),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update violation');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.violations });
    },
  });

  // Resolve violation mutation
  const resolveViolationMutation = useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const response = await fetch(`/api/ai/compliance/violations/${id}/resolve`, {
        method: 'POST',
      });
      
      if (!response.ok) {
        throw new Error('Failed to resolve violation');
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.violations });
    },
  });

  // Create framework mutation
  const createFrameworkMutation = useMutation({
    mutationFn: async (framework: Omit<ComplianceFramework, 'id' | 'createdAt' | 'updatedAt'>): Promise<ComplianceFramework> => {
      const response = await fetch('/api/ai/compliance/frameworks', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(framework),
      });
      
      if (!response.ok) {
        throw new Error('Failed to create framework');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.frameworks });
    },
  });

  // Update framework mutation
  const updateFrameworkMutation = useMutation({
    mutationFn: async ({ id, updates }: { id: string; updates: Partial<ComplianceFramework> }): Promise<ComplianceFramework> => {
      const response = await fetch(`/api/ai/compliance/frameworks/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(updates),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update framework');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.frameworks });
    },
  });

  // Delete framework mutation
  const deleteFrameworkMutation = useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const response = await fetch(`/api/ai/compliance/frameworks/${id}`, {
        method: 'DELETE',
      });
      
      if (!response.ok) {
        throw new Error('Failed to delete framework');
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.frameworks });
    },
  });

  // Create requirement mutation
  const createRequirementMutation = useMutation({
    mutationFn: async (requirement: Omit<ComplianceRequirement, 'id' | 'createdAt' | 'updatedAt'>): Promise<ComplianceRequirement> => {
      const response = await fetch('/api/ai/compliance/requirements', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requirement),
      });
      
      if (!response.ok) {
        throw new Error('Failed to create requirement');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.requirements });
    },
  });

  // Update requirement mutation
  const updateRequirementMutation = useMutation({
    mutationFn: async ({ id, updates }: { id: string; updates: Partial<ComplianceRequirement> }): Promise<ComplianceRequirement> => {
      const response = await fetch(`/api/ai/compliance/requirements/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(updates),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update requirement');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.requirements });
    },
  });

  // Delete requirement mutation
  const deleteRequirementMutation = useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const response = await fetch(`/api/ai/compliance/requirements/${id}`, {
        method: 'DELETE',
      });
      
      if (!response.ok) {
        throw new Error('Failed to delete requirement');
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.requirements });
    },
  });

  // Generate report mutation
  const generateReportMutation = useMutation({
    mutationFn: async ({ frameworkId, startDate, endDate }: { frameworkId: string; startDate: string; endDate: string }): Promise<ComplianceReport> => {
      const response = await fetch('/api/ai/compliance/reports/generate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ frameworkId, startDate, endDate }),
      });
      
      if (!response.ok) {
        throw new Error('Failed to generate report');
      }
      
      const result = await response.json();
      onReportGenerated?.(result);
      return result;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.reports });
    },
  });

  // Action functions
  const checkCompliance = useCallback(
    (request: ComplianceCheckRequest) => checkComplianceMutation.mutateAsync(request),
    [checkComplianceMutation]
  );

  const createViolation = useCallback(
    (violation: Omit<ComplianceViolation, 'id' | 'createdAt' | 'updatedAt'>) => 
      createViolationMutation.mutateAsync(violation),
    [createViolationMutation]
  );

  const updateViolation = useCallback(
    (id: string, updates: Partial<ComplianceViolation>) => 
      updateViolationMutation.mutateAsync({ id, updates }),
    [updateViolationMutation]
  );

  const resolveViolation = useCallback(
    (id: string) => resolveViolationMutation.mutateAsync(id),
    [resolveViolationMutation]
  );

  const createFramework = useCallback(
    (framework: Omit<ComplianceFramework, 'id' | 'createdAt' | 'updatedAt'>) => 
      createFrameworkMutation.mutateAsync(framework),
    [createFrameworkMutation]
  );

  const updateFramework = useCallback(
    (id: string, updates: Partial<ComplianceFramework>) => 
      updateFrameworkMutation.mutateAsync({ id, updates }),
    [updateFrameworkMutation]
  );

  const deleteFramework = useCallback(
    (id: string) => deleteFrameworkMutation.mutateAsync(id),
    [deleteFrameworkMutation]
  );

  const createRequirement = useCallback(
    (requirement: Omit<ComplianceRequirement, 'id' | 'createdAt' | 'updatedAt'>) => 
      createRequirementMutation.mutateAsync(requirement),
    [createRequirementMutation]
  );

  const updateRequirement = useCallback(
    (id: string, updates: Partial<ComplianceRequirement>) => 
      updateRequirementMutation.mutateAsync({ id, updates }),
    [updateRequirementMutation]
  );

  const deleteRequirement = useCallback(
    (id: string) => deleteRequirementMutation.mutateAsync(id),
    [deleteRequirementMutation]
  );

  const generateReport = useCallback(
    (frameworkId: string, startDate: string, endDate: string) => 
      generateReportMutation.mutateAsync({ frameworkId, startDate, endDate }),
    [generateReportMutation]
  );

  // Refresh functions
  const refresh = useCallback(() => {
    refetchViolations();
    refetchMetrics();
    refetchFrameworks();
    refetchRequirements();
    refetchReports();
  }, [refetchViolations, refetchMetrics, refetchFrameworks, refetchRequirements, refetchReports]);

  const refreshViolations = useCallback(() => refetchViolations(), [refetchViolations]);
  const refreshMetrics = useCallback(() => refetchMetrics(), [refetchMetrics]);
  const refreshFrameworks = useCallback(() => refetchFrameworks(), [refetchFrameworks]);
  const refreshRequirements = useCallback(() => refetchRequirements(), [refetchRequirements]);
  const refreshReports = useCallback(() => refetchReports(), [refetchReports]);

  // Computed values
  const criticalViolations = violations.filter(violation => violation.severity === 'CRITICAL');
  const highViolations = violations.filter(violation => violation.severity === 'HIGH');
  const activeViolations = violations.filter(violation => violation.status === 'ACTIVE');
  const resolvedViolations = violations.filter(violation => violation.status === 'RESOLVED');
  const violationCount = violations.length;
  const frameworkCount = frameworks.length;
  const requirementCount = requirements.length;
  const reportCount = reports.length;
  const complianceScore = metrics?.complianceScore || 0;
  const violationTrend = metrics?.violationTrend || 'stable';
  const complianceTrend = metrics?.complianceTrend || 'stable';

  // Overall loading state
  const isLoading = isViolationsLoading || isMetricsLoading || isFrameworksLoading || isRequirementsLoading || isReportsLoading;

  // Overall error state
  const overallError = error || violationsError || metricsError || frameworksError || requirementsError || reportsError;

  return {
    // Data
    violations,
    metrics,
    frameworks,
    requirements,
    reports,
    
    // Loading states
    isLoading,
    isViolationsLoading,
    isMetricsLoading,
    isFrameworksLoading,
    isRequirementsLoading,
    isReportsLoading,
    
    // Error states
    error: overallError,
    violationsError,
    metricsError,
    frameworksError,
    requirementsError,
    reportsError,
    
    // Actions
    checkCompliance,
    createViolation,
    updateViolation,
    resolveViolation,
    createFramework,
    updateFramework,
    deleteFramework,
    createRequirement,
    updateRequirement,
    deleteRequirement,
    generateReport,
    
    // Utilities
    refresh,
    refreshViolations,
    refreshMetrics,
    refreshFrameworks,
    refreshRequirements,
    refreshReports,
    
    // Computed values
    criticalViolations,
    highViolations,
    activeViolations,
    resolvedViolations,
    violationCount,
    frameworkCount,
    requirementCount,
    reportCount,
    complianceScore,
    violationTrend,
    complianceTrend,
  };
};

export default useAICompliance;
export { useAICompliance };