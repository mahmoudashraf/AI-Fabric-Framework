import { useState, useEffect, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { 
  DataSubject, 
  PrivacyViolation, 
  DataClassification, 
  PrivacySettings,
  PrivacyMetrics,
  ConsentRequest,
  ConsentResponse,
  DataRetentionPolicy,
  PrivacyAudit
} from '../types/ai';

interface UseAIDataPrivacyOptions {
  refreshInterval?: number;
  autoRefresh?: boolean;
  onViolationDetected?: (violation: PrivacyViolation) => void;
  onConsentUpdated?: (subject: DataSubject) => void;
}

interface UseAIDataPrivacyReturn {
  // Data
  dataSubjects: DataSubject[];
  violations: PrivacyViolation[];
  classifications: DataClassification[];
  settings: PrivacySettings | null;
  metrics: PrivacyMetrics | null;
  retentionPolicies: DataRetentionPolicy[];
  audits: PrivacyAudit[];
  
  // Loading states
  isLoading: boolean;
  isDataSubjectsLoading: boolean;
  isViolationsLoading: boolean;
  isClassificationsLoading: boolean;
  isSettingsLoading: boolean;
  isMetricsLoading: boolean;
  isRetentionPoliciesLoading: boolean;
  isAuditsLoading: boolean;
  
  // Error states
  error: string | null;
  dataSubjectsError: string | null;
  violationsError: string | null;
  classificationsError: string | null;
  settingsError: string | null;
  metricsError: string | null;
  retentionPoliciesError: string | null;
  auditsError: string | null;
  
  // Actions
  updateConsent: (request: ConsentRequest) => Promise<ConsentResponse>;
  createViolation: (violation: Omit<PrivacyViolation, 'id' | 'createdAt' | 'updatedAt'>) => Promise<PrivacyViolation>;
  updateViolation: (id: string, updates: Partial<PrivacyViolation>) => Promise<PrivacyViolation>;
  resolveViolation: (id: string) => Promise<void>;
  createClassification: (classification: Omit<DataClassification, 'id' | 'createdAt' | 'updatedAt'>) => Promise<DataClassification>;
  updateClassification: (id: string, updates: Partial<DataClassification>) => Promise<DataClassification>;
  deleteClassification: (id: string) => Promise<void>;
  updateSettings: (settings: Partial<PrivacySettings>) => Promise<PrivacySettings>;
  createRetentionPolicy: (policy: Omit<DataRetentionPolicy, 'id' | 'createdAt' | 'updatedAt'>) => Promise<DataRetentionPolicy>;
  updateRetentionPolicy: (id: string, updates: Partial<DataRetentionPolicy>) => Promise<DataRetentionPolicy>;
  deleteRetentionPolicy: (id: string) => Promise<void>;
  runAudit: (auditType: string) => Promise<PrivacyAudit>;
  
  // Utilities
  refresh: () => void;
  refreshDataSubjects: () => void;
  refreshViolations: () => void;
  refreshClassifications: () => void;
  refreshSettings: () => void;
  refreshMetrics: () => void;
  refreshRetentionPolicies: () => void;
  refreshAudits: () => void;
  
  // Computed values
  activeViolations: PrivacyViolation[];
  resolvedViolations: PrivacyViolation[];
  criticalViolations: PrivacyViolation[];
  highViolations: PrivacyViolation[];
  consentGivenSubjects: DataSubject[];
  consentWithdrawnSubjects: DataSubject[];
  dataSubjectCount: number;
  violationCount: number;
  classificationCount: number;
  retentionPolicyCount: number;
  auditCount: number;
  complianceScore: number;
  consentRate: number;
  violationTrend: 'increasing' | 'decreasing' | 'stable';
  complianceTrend: 'improving' | 'declining' | 'stable';
}

const useAIDataPrivacy = (options: UseAIDataPrivacyOptions = {}): UseAIDataPrivacyReturn => {
  const {
    refreshInterval = 30000,
    autoRefresh = true,
    onViolationDetected,
    onConsentUpdated,
  } = options;

  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  // Query keys
  const queryKeys = {
    dataSubjects: ['ai-privacy', 'data-subjects'],
    violations: ['ai-privacy', 'violations'],
    classifications: ['ai-privacy', 'classifications'],
    settings: ['ai-privacy', 'settings'],
    metrics: ['ai-privacy', 'metrics'],
    retentionPolicies: ['ai-privacy', 'retention-policies'],
    audits: ['ai-privacy', 'audits'],
  };

  // Fetch data subjects
  const {
    data: dataSubjects = [],
    isLoading: isDataSubjectsLoading,
    error: dataSubjectsError,
    refetch: refetchDataSubjects,
  } = useQuery({
    queryKey: queryKeys.dataSubjects,
    queryFn: async (): Promise<DataSubject[]> => {
      const response = await fetch('/api/ai/privacy/data-subjects');
      if (!response.ok) {
        throw new Error('Failed to fetch data subjects');
      }
      return response.json();
    },
    refetchInterval: autoRefresh ? refreshInterval : false,
    refetchIntervalInBackground: false,
  });

  // Fetch violations
  const {
    data: violations = [],
    isLoading: isViolationsLoading,
    error: violationsError,
    refetch: refetchViolations,
  } = useQuery({
    queryKey: queryKeys.violations,
    queryFn: async (): Promise<PrivacyViolation[]> => {
      const response = await fetch('/api/ai/privacy/violations');
      if (!response.ok) {
        throw new Error('Failed to fetch privacy violations');
      }
      return response.json();
    },
    refetchInterval: autoRefresh ? refreshInterval : false,
    refetchIntervalInBackground: false,
  });

  // Fetch classifications
  const {
    data: classifications = [],
    isLoading: isClassificationsLoading,
    error: classificationsError,
    refetch: refetchClassifications,
  } = useQuery({
    queryKey: queryKeys.classifications,
    queryFn: async (): Promise<DataClassification[]> => {
      const response = await fetch('/api/ai/privacy/classifications');
      if (!response.ok) {
        throw new Error('Failed to fetch data classifications');
      }
      return response.json();
    },
  });

  // Fetch settings
  const {
    data: settings,
    isLoading: isSettingsLoading,
    error: settingsError,
    refetch: refetchSettings,
  } = useQuery({
    queryKey: queryKeys.settings,
    queryFn: async (): Promise<PrivacySettings> => {
      const response = await fetch('/api/ai/privacy/settings');
      if (!response.ok) {
        throw new Error('Failed to fetch privacy settings');
      }
      return response.json();
    },
  });

  // Fetch metrics
  const {
    data: metrics,
    isLoading: isMetricsLoading,
    error: metricsError,
    refetch: refetchMetrics,
  } = useQuery({
    queryKey: queryKeys.metrics,
    queryFn: async (): Promise<PrivacyMetrics> => {
      const response = await fetch('/api/ai/privacy/metrics');
      if (!response.ok) {
        throw new Error('Failed to fetch privacy metrics');
      }
      return response.json();
    },
    refetchInterval: autoRefresh ? refreshInterval : false,
    refetchIntervalInBackground: false,
  });

  // Fetch retention policies
  const {
    data: retentionPolicies = [],
    isLoading: isRetentionPoliciesLoading,
    error: retentionPoliciesError,
    refetch: refetchRetentionPolicies,
  } = useQuery({
    queryKey: queryKeys.retentionPolicies,
    queryFn: async (): Promise<DataRetentionPolicy[]> => {
      const response = await fetch('/api/ai/privacy/retention-policies');
      if (!response.ok) {
        throw new Error('Failed to fetch retention policies');
      }
      return response.json();
    },
  });

  // Fetch audits
  const {
    data: audits = [],
    isLoading: isAuditsLoading,
    error: auditsError,
    refetch: refetchAudits,
  } = useQuery({
    queryKey: queryKeys.audits,
    queryFn: async (): Promise<PrivacyAudit[]> => {
      const response = await fetch('/api/ai/privacy/audits');
      if (!response.ok) {
        throw new Error('Failed to fetch privacy audits');
      }
      return response.json();
    },
  });

  // Update consent mutation
  const updateConsentMutation = useMutation({
    mutationFn: async (request: ConsentRequest): Promise<ConsentResponse> => {
      const response = await fetch('/api/ai/privacy/consent', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update consent');
      }
      
      const result = await response.json();
      onConsentUpdated?.(result.dataSubject);
      return result;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.dataSubjects });
      queryClient.invalidateQueries({ queryKey: queryKeys.metrics });
    },
  });

  // Create violation mutation
  const createViolationMutation = useMutation({
    mutationFn: async (violation: Omit<PrivacyViolation, 'id' | 'createdAt' | 'updatedAt'>): Promise<PrivacyViolation> => {
      const response = await fetch('/api/ai/privacy/violations', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(violation),
      });
      
      if (!response.ok) {
        throw new Error('Failed to create privacy violation');
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
    mutationFn: async ({ id, updates }: { id: string; updates: Partial<PrivacyViolation> }): Promise<PrivacyViolation> => {
      const response = await fetch(`/api/ai/privacy/violations/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(updates),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update privacy violation');
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
      const response = await fetch(`/api/ai/privacy/violations/${id}/resolve`, {
        method: 'POST',
      });
      
      if (!response.ok) {
        throw new Error('Failed to resolve privacy violation');
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.violations });
    },
  });

  // Create classification mutation
  const createClassificationMutation = useMutation({
    mutationFn: async (classification: Omit<DataClassification, 'id' | 'createdAt' | 'updatedAt'>): Promise<DataClassification> => {
      const response = await fetch('/api/ai/privacy/classifications', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(classification),
      });
      
      if (!response.ok) {
        throw new Error('Failed to create data classification');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.classifications });
    },
  });

  // Update classification mutation
  const updateClassificationMutation = useMutation({
    mutationFn: async ({ id, updates }: { id: string; updates: Partial<DataClassification> }): Promise<DataClassification> => {
      const response = await fetch(`/api/ai/privacy/classifications/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(updates),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update data classification');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.classifications });
    },
  });

  // Delete classification mutation
  const deleteClassificationMutation = useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const response = await fetch(`/api/ai/privacy/classifications/${id}`, {
        method: 'DELETE',
      });
      
      if (!response.ok) {
        throw new Error('Failed to delete data classification');
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.classifications });
    },
  });

  // Update settings mutation
  const updateSettingsMutation = useMutation({
    mutationFn: async (settings: Partial<PrivacySettings>): Promise<PrivacySettings> => {
      const response = await fetch('/api/ai/privacy/settings', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(settings),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update privacy settings');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.settings });
    },
  });

  // Create retention policy mutation
  const createRetentionPolicyMutation = useMutation({
    mutationFn: async (policy: Omit<DataRetentionPolicy, 'id' | 'createdAt' | 'updatedAt'>): Promise<DataRetentionPolicy> => {
      const response = await fetch('/api/ai/privacy/retention-policies', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(policy),
      });
      
      if (!response.ok) {
        throw new Error('Failed to create retention policy');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.retentionPolicies });
    },
  });

  // Update retention policy mutation
  const updateRetentionPolicyMutation = useMutation({
    mutationFn: async ({ id, updates }: { id: string; updates: Partial<DataRetentionPolicy> }): Promise<DataRetentionPolicy> => {
      const response = await fetch(`/api/ai/privacy/retention-policies/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(updates),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update retention policy');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.retentionPolicies });
    },
  });

  // Delete retention policy mutation
  const deleteRetentionPolicyMutation = useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const response = await fetch(`/api/ai/privacy/retention-policies/${id}`, {
        method: 'DELETE',
      });
      
      if (!response.ok) {
        throw new Error('Failed to delete retention policy');
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.retentionPolicies });
    },
  });

  // Run audit mutation
  const runAuditMutation = useMutation({
    mutationFn: async (auditType: string): Promise<PrivacyAudit> => {
      const response = await fetch('/api/ai/privacy/audits', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ auditType }),
      });
      
      if (!response.ok) {
        throw new Error('Failed to run privacy audit');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.audits });
    },
  });

  // Action functions
  const updateConsent = useCallback(
    (request: ConsentRequest) => updateConsentMutation.mutateAsync(request),
    [updateConsentMutation]
  );

  const createViolation = useCallback(
    (violation: Omit<PrivacyViolation, 'id' | 'createdAt' | 'updatedAt'>) => 
      createViolationMutation.mutateAsync(violation),
    [createViolationMutation]
  );

  const updateViolation = useCallback(
    (id: string, updates: Partial<PrivacyViolation>) => 
      updateViolationMutation.mutateAsync({ id, updates }),
    [updateViolationMutation]
  );

  const resolveViolation = useCallback(
    (id: string) => resolveViolationMutation.mutateAsync(id),
    [resolveViolationMutation]
  );

  const createClassification = useCallback(
    (classification: Omit<DataClassification, 'id' | 'createdAt' | 'updatedAt'>) => 
      createClassificationMutation.mutateAsync(classification),
    [createClassificationMutation]
  );

  const updateClassification = useCallback(
    (id: string, updates: Partial<DataClassification>) => 
      updateClassificationMutation.mutateAsync({ id, updates }),
    [updateClassificationMutation]
  );

  const deleteClassification = useCallback(
    (id: string) => deleteClassificationMutation.mutateAsync(id),
    [deleteClassificationMutation]
  );

  const updateSettings = useCallback(
    (settings: Partial<PrivacySettings>) => 
      updateSettingsMutation.mutateAsync(settings),
    [updateSettingsMutation]
  );

  const createRetentionPolicy = useCallback(
    (policy: Omit<DataRetentionPolicy, 'id' | 'createdAt' | 'updatedAt'>) => 
      createRetentionPolicyMutation.mutateAsync(policy),
    [createRetentionPolicyMutation]
  );

  const updateRetentionPolicy = useCallback(
    (id: string, updates: Partial<DataRetentionPolicy>) => 
      updateRetentionPolicyMutation.mutateAsync({ id, updates }),
    [updateRetentionPolicyMutation]
  );

  const deleteRetentionPolicy = useCallback(
    (id: string) => deleteRetentionPolicyMutation.mutateAsync(id),
    [deleteRetentionPolicyMutation]
  );

  const runAudit = useCallback(
    (auditType: string) => runAuditMutation.mutateAsync(auditType),
    [runAuditMutation]
  );

  // Refresh functions
  const refresh = useCallback(() => {
    refetchDataSubjects();
    refetchViolations();
    refetchClassifications();
    refetchSettings();
    refetchMetrics();
    refetchRetentionPolicies();
    refetchAudits();
  }, [refetchDataSubjects, refetchViolations, refetchClassifications, refetchSettings, refetchMetrics, refetchRetentionPolicies, refetchAudits]);

  const refreshDataSubjects = useCallback(() => refetchDataSubjects(), [refetchDataSubjects]);
  const refreshViolations = useCallback(() => refetchViolations(), [refetchViolations]);
  const refreshClassifications = useCallback(() => refetchClassifications(), [refetchClassifications]);
  const refreshSettings = useCallback(() => refetchSettings(), [refetchSettings]);
  const refreshMetrics = useCallback(() => refetchMetrics(), [refetchMetrics]);
  const refreshRetentionPolicies = useCallback(() => refetchRetentionPolicies(), [refetchRetentionPolicies]);
  const refreshAudits = useCallback(() => refetchAudits(), [refetchAudits]);

  // Computed values
  const activeViolations = violations.filter(violation => violation.status === 'ACTIVE');
  const resolvedViolations = violations.filter(violation => violation.status === 'RESOLVED');
  const criticalViolations = violations.filter(violation => violation.severity === 'CRITICAL');
  const highViolations = violations.filter(violation => violation.severity === 'HIGH');
  const consentGivenSubjects = dataSubjects.filter(subject => subject.consentGiven);
  const consentWithdrawnSubjects = dataSubjects.filter(subject => !subject.consentGiven);
  const dataSubjectCount = dataSubjects.length;
  const violationCount = violations.length;
  const classificationCount = classifications.length;
  const retentionPolicyCount = retentionPolicies.length;
  const auditCount = audits.length;
  const complianceScore = metrics?.complianceScore || 0;
  const consentRate = metrics?.consentRate || 0;
  const violationTrend = metrics?.violationTrend || 'stable';
  const complianceTrend = metrics?.complianceTrend || 'stable';

  // Overall loading state
  const isLoading = isDataSubjectsLoading || isViolationsLoading || isClassificationsLoading || isSettingsLoading || isMetricsLoading || isRetentionPoliciesLoading || isAuditsLoading;

  // Overall error state
  const overallError = error || dataSubjectsError || violationsError || classificationsError || settingsError || metricsError || retentionPoliciesError || auditsError;

  return {
    // Data
    dataSubjects,
    violations,
    classifications,
    settings,
    metrics,
    retentionPolicies,
    audits,
    
    // Loading states
    isLoading,
    isDataSubjectsLoading,
    isViolationsLoading,
    isClassificationsLoading,
    isSettingsLoading,
    isMetricsLoading,
    isRetentionPoliciesLoading,
    isAuditsLoading,
    
    // Error states
    error: overallError,
    dataSubjectsError,
    violationsError,
    classificationsError,
    settingsError,
    metricsError,
    retentionPoliciesError,
    auditsError,
    
    // Actions
    updateConsent,
    createViolation,
    updateViolation,
    resolveViolation,
    createClassification,
    updateClassification,
    deleteClassification,
    updateSettings,
    createRetentionPolicy,
    updateRetentionPolicy,
    deleteRetentionPolicy,
    runAudit,
    
    // Utilities
    refresh,
    refreshDataSubjects,
    refreshViolations,
    refreshClassifications,
    refreshSettings,
    refreshMetrics,
    refreshRetentionPolicies,
    refreshAudits,
    
    // Computed values
    activeViolations,
    resolvedViolations,
    criticalViolations,
    highViolations,
    consentGivenSubjects,
    consentWithdrawnSubjects,
    dataSubjectCount,
    violationCount,
    classificationCount,
    retentionPolicyCount,
    auditCount,
    complianceScore,
    consentRate,
    violationTrend,
    complianceTrend,
  };
};

export default useAIDataPrivacy;