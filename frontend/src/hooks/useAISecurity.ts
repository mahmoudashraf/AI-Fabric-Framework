import { useState, useEffect, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { 
  SecurityThreat, 
  SecurityMetrics, 
  ThreatDetectionRequest, 
  ThreatDetectionResponse,
  SecurityIncident,
  SecurityPolicy,
  SecurityConfiguration
} from '../types/ai';

interface UseAISecurityOptions {
  refreshInterval?: number;
  autoRefresh?: boolean;
  onThreatDetected?: (threat: SecurityThreat) => void;
  onIncidentCreated?: (incident: SecurityIncident) => void;
}

interface UseAISecurityReturn {
  // Data
  threats: SecurityThreat[];
  metrics: SecurityMetrics | null;
  incidents: SecurityIncident[];
  policies: SecurityPolicy[];
  configuration: SecurityConfiguration | null;
  
  // Loading states
  isLoading: boolean;
  isThreatsLoading: boolean;
  isMetricsLoading: boolean;
  isIncidentsLoading: boolean;
  isPoliciesLoading: boolean;
  isConfigurationLoading: boolean;
  
  // Error states
  error: string | null;
  threatsError: string | null;
  metricsError: string | null;
  incidentsError: string | null;
  policiesError: string | null;
  configurationError: string | null;
  
  // Actions
  detectThreats: (request: ThreatDetectionRequest) => Promise<ThreatDetectionResponse>;
  createIncident: (incident: Omit<SecurityIncident, 'id' | 'createdAt' | 'updatedAt'>) => Promise<SecurityIncident>;
  updateIncident: (id: string, updates: Partial<SecurityIncident>) => Promise<SecurityIncident>;
  resolveIncident: (id: string) => Promise<void>;
  createPolicy: (policy: Omit<SecurityPolicy, 'id' | 'createdAt' | 'updatedAt'>) => Promise<SecurityPolicy>;
  updatePolicy: (id: string, updates: Partial<SecurityPolicy>) => Promise<SecurityPolicy>;
  deletePolicy: (id: string) => Promise<void>;
  updateConfiguration: (config: Partial<SecurityConfiguration>) => Promise<SecurityConfiguration>;
  
  // Utilities
  refresh: () => void;
  refreshThreats: () => void;
  refreshMetrics: () => void;
  refreshIncidents: () => void;
  refreshPolicies: () => void;
  refreshConfiguration: () => void;
  
  // Computed values
  criticalThreats: SecurityThreat[];
  highThreats: SecurityThreat[];
  activeIncidents: SecurityIncident[];
  resolvedIncidents: SecurityIncident[];
  threatCount: number;
  incidentCount: number;
  policyCount: number;
  averageResponseTime: number;
  threatTrend: 'increasing' | 'decreasing' | 'stable';
  incidentTrend: 'increasing' | 'decreasing' | 'stable';
}

const useAISecurity = (options: UseAISecurityOptions = {}): UseAISecurityReturn => {
  const {
    refreshInterval = 30000,
    autoRefresh = true,
    onThreatDetected,
    onIncidentCreated,
  } = options;

  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  // Query keys
  const queryKeys = {
    threats: ['ai-security', 'threats'],
    metrics: ['ai-security', 'metrics'],
    incidents: ['ai-security', 'incidents'],
    policies: ['ai-security', 'policies'],
    configuration: ['ai-security', 'configuration'],
  };

  // Fetch threats
  const {
    data: threats = [],
    isLoading: isThreatsLoading,
    error: threatsError,
    refetch: refetchThreats,
  } = useQuery({
    queryKey: queryKeys.threats,
    queryFn: async (): Promise<SecurityThreat[]> => {
      const response = await fetch('/api/ai/security/threats');
      if (!response.ok) {
        throw new Error('Failed to fetch threats');
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
    queryFn: async (): Promise<SecurityMetrics> => {
      const response = await fetch('/api/ai/security/metrics');
      if (!response.ok) {
        throw new Error('Failed to fetch metrics');
      }
      return response.json();
    },
    refetchInterval: autoRefresh ? refreshInterval : false,
    refetchIntervalInBackground: false,
  });

  // Fetch incidents
  const {
    data: incidents = [],
    isLoading: isIncidentsLoading,
    error: incidentsError,
    refetch: refetchIncidents,
  } = useQuery({
    queryKey: queryKeys.incidents,
    queryFn: async (): Promise<SecurityIncident[]> => {
      const response = await fetch('/api/ai/security/incidents');
      if (!response.ok) {
        throw new Error('Failed to fetch incidents');
      }
      return response.json();
    },
    refetchInterval: autoRefresh ? refreshInterval : false,
    refetchIntervalInBackground: false,
  });

  // Fetch policies
  const {
    data: policies = [],
    isLoading: isPoliciesLoading,
    error: policiesError,
    refetch: refetchPolicies,
  } = useQuery({
    queryKey: queryKeys.policies,
    queryFn: async (): Promise<SecurityPolicy[]> => {
      const response = await fetch('/api/ai/security/policies');
      if (!response.ok) {
        throw new Error('Failed to fetch policies');
      }
      return response.json();
    },
  });

  // Fetch configuration
  const {
    data: configuration,
    isLoading: isConfigurationLoading,
    error: configurationError,
    refetch: refetchConfiguration,
  } = useQuery({
    queryKey: queryKeys.configuration,
    queryFn: async (): Promise<SecurityConfiguration> => {
      const response = await fetch('/api/ai/security/configuration');
      if (!response.ok) {
        throw new Error('Failed to fetch configuration');
      }
      return response.json();
    },
  });

  // Detect threats mutation
  const detectThreatsMutation = useMutation({
    mutationFn: async (request: ThreatDetectionRequest): Promise<ThreatDetectionResponse> => {
      const response = await fetch('/api/ai/security/detect-threats', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      });
      
      if (!response.ok) {
        throw new Error('Failed to detect threats');
      }
      
      const result = await response.json();
      
      // Trigger callback if threats detected
      if (result.threats && result.threats.length > 0) {
        result.threats.forEach((threat: SecurityThreat) => {
          onThreatDetected?.(threat);
        });
      }
      
      return result;
    },
    onSuccess: () => {
      // Invalidate and refetch threats
      queryClient.invalidateQueries({ queryKey: queryKeys.threats });
    },
  });

  // Create incident mutation
  const createIncidentMutation = useMutation({
    mutationFn: async (incident: Omit<SecurityIncident, 'id' | 'createdAt' | 'updatedAt'>): Promise<SecurityIncident> => {
      const response = await fetch('/api/ai/security/incidents', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(incident),
      });
      
      if (!response.ok) {
        throw new Error('Failed to create incident');
      }
      
      const result = await response.json();
      onIncidentCreated?.(result);
      return result;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.incidents });
    },
  });

  // Update incident mutation
  const updateIncidentMutation = useMutation({
    mutationFn: async ({ id, updates }: { id: string; updates: Partial<SecurityIncident> }): Promise<SecurityIncident> => {
      const response = await fetch(`/api/ai/security/incidents/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(updates),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update incident');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.incidents });
    },
  });

  // Resolve incident mutation
  const resolveIncidentMutation = useMutation({
    mutationFn: async (id: string): Promise<void> => {
      const response = await fetch(`/api/ai/security/incidents/${id}/resolve`, {
        method: 'POST',
      });
      
      if (!response.ok) {
        throw new Error('Failed to resolve incident');
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.incidents });
    },
  });

  // Create policy mutation
  const createPolicyMutation = useMutation({
    mutationFn: async (policy: Omit<SecurityPolicy, 'id' | 'createdAt' | 'updatedAt'>): Promise<SecurityPolicy> => {
      const response = await fetch('/api/ai/security/policies', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(policy),
      });
      
      if (!response.ok) {
        throw new Error('Failed to create policy');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.policies });
    },
  });

  // Update policy mutation
  const updatePolicyMutation = useMutation({
    mutationFn: async ({ id, updates }: { id: string; updates: Partial<SecurityPolicy> }): Promise<SecurityPolicy> => {
      const response = await fetch(`/api/ai/security/policies/${id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(updates),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update policy');
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
      const response = await fetch(`/api/ai/security/policies/${id}`, {
        method: 'DELETE',
      });
      
      if (!response.ok) {
        throw new Error('Failed to delete policy');
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.policies });
    },
  });

  // Update configuration mutation
  const updateConfigurationMutation = useMutation({
    mutationFn: async (config: Partial<SecurityConfiguration>): Promise<SecurityConfiguration> => {
      const response = await fetch('/api/ai/security/configuration', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(config),
      });
      
      if (!response.ok) {
        throw new Error('Failed to update configuration');
      }
      
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.configuration });
    },
  });

  // Action functions
  const detectThreats = useCallback(
    (request: ThreatDetectionRequest) => detectThreatsMutation.mutateAsync(request),
    [detectThreatsMutation]
  );

  const createIncident = useCallback(
    (incident: Omit<SecurityIncident, 'id' | 'createdAt' | 'updatedAt'>) => 
      createIncidentMutation.mutateAsync(incident),
    [createIncidentMutation]
  );

  const updateIncident = useCallback(
    (id: string, updates: Partial<SecurityIncident>) => 
      updateIncidentMutation.mutateAsync({ id, updates }),
    [updateIncidentMutation]
  );

  const resolveIncident = useCallback(
    (id: string) => resolveIncidentMutation.mutateAsync(id),
    [resolveIncidentMutation]
  );

  const createPolicy = useCallback(
    (policy: Omit<SecurityPolicy, 'id' | 'createdAt' | 'updatedAt'>) => 
      createPolicyMutation.mutateAsync(policy),
    [createPolicyMutation]
  );

  const updatePolicy = useCallback(
    (id: string, updates: Partial<SecurityPolicy>) => 
      updatePolicyMutation.mutateAsync({ id, updates }),
    [updatePolicyMutation]
  );

  const deletePolicy = useCallback(
    (id: string) => deletePolicyMutation.mutateAsync(id),
    [deletePolicyMutation]
  );

  const updateConfiguration = useCallback(
    (config: Partial<SecurityConfiguration>) => 
      updateConfigurationMutation.mutateAsync(config),
    [updateConfigurationMutation]
  );

  // Refresh functions
  const refresh = useCallback(() => {
    refetchThreats();
    refetchMetrics();
    refetchIncidents();
    refetchPolicies();
    refetchConfiguration();
  }, [refetchThreats, refetchMetrics, refetchIncidents, refetchPolicies, refetchConfiguration]);

  const refreshThreats = useCallback(() => refetchThreats(), [refetchThreats]);
  const refreshMetrics = useCallback(() => refetchMetrics(), [refetchMetrics]);
  const refreshIncidents = useCallback(() => refetchIncidents(), [refetchIncidents]);
  const refreshPolicies = useCallback(() => refetchPolicies(), [refetchPolicies]);
  const refreshConfiguration = useCallback(() => refetchConfiguration(), [refetchConfiguration]);

  // Computed values
  const criticalThreats = threats.filter(threat => threat.severity === 'CRITICAL');
  const highThreats = threats.filter(threat => threat.severity === 'HIGH');
  const activeIncidents = incidents.filter(incident => incident.status === 'ACTIVE');
  const resolvedIncidents = incidents.filter(incident => incident.status === 'RESOLVED');
  const threatCount = threats.length;
  const incidentCount = incidents.length;
  const policyCount = policies.length;
  const averageResponseTime = metrics?.averageResponseTime || 0;

  // Calculate trends (simplified)
  const threatTrend = metrics?.threatTrend || 'stable';
  const incidentTrend = metrics?.incidentTrend || 'stable';

  // Overall loading state
  const isLoading = isThreatsLoading || isMetricsLoading || isIncidentsLoading || isPoliciesLoading || isConfigurationLoading;

  // Overall error state
  const overallError = error || threatsError || metricsError || incidentsError || policiesError || configurationError;

  return {
    // Data
    threats,
    metrics,
    incidents,
    policies,
    configuration,
    
    // Loading states
    isLoading,
    isThreatsLoading,
    isMetricsLoading,
    isIncidentsLoading,
    isPoliciesLoading,
    isConfigurationLoading,
    
    // Error states
    error: overallError,
    threatsError,
    metricsError,
    incidentsError,
    policiesError,
    configurationError,
    
    // Actions
    detectThreats,
    createIncident,
    updateIncident,
    resolveIncident,
    createPolicy,
    updatePolicy,
    deletePolicy,
    updateConfiguration,
    
    // Utilities
    refresh,
    refreshThreats,
    refreshMetrics,
    refreshIncidents,
    refreshPolicies,
    refreshConfiguration,
    
    // Computed values
    criticalThreats,
    highThreats,
    activeIncidents,
    resolvedIncidents,
    threatCount,
    incidentCount,
    policyCount,
    averageResponseTime,
    threatTrend,
    incidentTrend,
  };
};

export default useAISecurity;
export { useAISecurity };