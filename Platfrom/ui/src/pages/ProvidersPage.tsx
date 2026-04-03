import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Divider,
  FormControlLabel,
  Grid,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import {
  fetchDeploymentProviderConnectivity,
  fetchDeploymentDraft,
  fetchDeploymentSecretUsage,
  probeDeploymentProviderConnectivity,
  updateDeploymentDraft,
  validateDeploymentDraft,
  type DeploymentSecretUsageItemSummary,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useDeploymentWorkspaceEditorState } from '../workspace/useDeploymentWorkspaceEditorState'

type ProviderFormState = {
  llmProvider: string
  embeddingProvider: string
  vectorStrategy: string
  vectorProvisioningMode: string
  runtimeProfile: string
  connectorProfile: string
  enableFallback: boolean
  orchestrationLlmProvider: string
  orchestrationModel: string
  orchestrationTemperature: string
  orchestrationMaxTokens: string
  orchestrationTimeout: string
  generationLlmProvider: string
  generationModel: string
  generationTemperature: string
  generationMaxTokens: string
  generationTimeout: string
  openaiValidateOnStartup: boolean
  openaiBaseUrl: string
  openaiModel: string
  openaiEmbeddingModel: string
  openaiEmbeddingDimensions: string
  openaiMaxTokens: string
  openaiTemperature: string
  openaiTimeout: string
  openaiPriority: string
  anthropicBaseUrl: string
  anthropicModel: string
  anthropicMaxTokens: string
  anthropicTemperature: string
  anthropicTimeout: string
  anthropicPriority: string
  azureValidateOnStartup: boolean
  azureEndpoint: string
  azureDeploymentName: string
  azureEmbeddingDeploymentName: string
  azureApiVersion: string
  azureTimeout: string
  azurePriority: string
  cohereValidateOnStartup: boolean
  cohereBaseUrl: string
  cohereModel: string
  cohereEmbeddingModel: string
  cohereMaxTokens: string
  cohereTemperature: string
  cohereTimeout: string
  coherePriority: string
  geminiValidateOnStartup: boolean
  geminiBaseUrl: string
  geminiModel: string
  geminiEmbeddingModel: string
  geminiMaxTokens: string
  geminiTemperature: string
  geminiTimeout: string
  geminiPriority: string
  onnxModelAlias: string
  onnxModelPath: string
  onnxTokenizerPath: string
  onnxMaxSequenceLength: string
  onnxUseGpu: boolean
  qdrantHost: string
  qdrantPort: string
  qdrantGrpcPort: string
  qdrantTimeout: string
  qdrantPreferGrpc: boolean
  qdrantManagedCollectionsEnabled: boolean
  qdrantCloudAccountId: string
  qdrantCloudProviderId: string
  qdrantCloudRegionId: string
  qdrantCloudPackageId: string
  qdrantCloudClusterNameOverride: string
  restEmbeddingValidateOnStartup: boolean
  restEmbeddingBaseUrl: string
  restEmbeddingEndpoint: string
  restEmbeddingBatchEndpoint: string
  restEmbeddingModel: string
  restEmbeddingTimeoutMs: string
  pineconeEnvironment: string
  pineconeIndexName: string
  pineconeProjectId: string
  pineconeApiHost: string
  pineconeDimensions: string
  pineconeManagedIndexEnabled: boolean
  pineconeCloud: string
  pineconeRegion: string
  pineconeMetric: string
  pineconeDeletionProtectionEnabled: boolean
  weaviateScheme: string
  weaviateHost: string
  weaviatePort: string
  weaviateTimeout: string
  weaviateConsistencyLevelStrong: boolean
  milvusHost: string
  milvusPort: string
  milvusDatabaseName: string
  milvusTimeout: string
  milvusSecure: boolean
  milvusFlushOnWrite: boolean
  zillizCloudProjectId: string
  zillizCloudRegionId: string
  zillizCloudClusterPlan: string
  zillizCloudCuType: string
  zillizCloudCuSize: string
  zillizCloudClusterNameOverride: string
}

type SummaryItem = {
  label: string
  value: string
}

type ManagedVectorDraftSummary = {
  enabled: boolean
  mode: string
  targets: string[]
  message: string
}

type VendorSetupGuide = {
  key: string
  severity: 'info' | 'warning' | 'success'
  title: string
  lines: string[]
}

const DEFAULT_PROVIDER_FORM_STATE: ProviderFormState = {
  llmProvider: 'openai',
  embeddingProvider: 'openai',
  vectorStrategy: 'lucene',
  vectorProvisioningMode: 'LOCAL_MANAGED',
  runtimeProfile: 'runtime-managed',
  connectorProfile: 'connector-hosted',
  enableFallback: true,
  orchestrationLlmProvider: '',
  orchestrationModel: '',
  orchestrationTemperature: '',
  orchestrationMaxTokens: '',
  orchestrationTimeout: '',
  generationLlmProvider: '',
  generationModel: '',
  generationTemperature: '',
  generationMaxTokens: '',
  generationTimeout: '',
  openaiValidateOnStartup: false,
  openaiBaseUrl: '',
  openaiModel: 'gpt-4o-mini',
  openaiEmbeddingModel: 'text-embedding-3-small',
  openaiEmbeddingDimensions: '1536',
  openaiMaxTokens: '',
  openaiTemperature: '',
  openaiTimeout: '',
  openaiPriority: '',
  anthropicBaseUrl: '',
  anthropicModel: 'claude-3-haiku-20240307',
  anthropicMaxTokens: '',
  anthropicTemperature: '',
  anthropicTimeout: '',
  anthropicPriority: '',
  azureValidateOnStartup: false,
  azureEndpoint: '',
  azureDeploymentName: '',
  azureEmbeddingDeploymentName: '',
  azureApiVersion: '2024-02-15-preview',
  azureTimeout: '',
  azurePriority: '',
  cohereValidateOnStartup: false,
  cohereBaseUrl: '',
  cohereModel: 'command-r7b-12-2024',
  cohereEmbeddingModel: 'embed-english-v3.0',
  cohereMaxTokens: '',
  cohereTemperature: '',
  cohereTimeout: '',
  coherePriority: '',
  geminiValidateOnStartup: false,
  geminiBaseUrl: '',
  geminiModel: 'gemini-1.5-flash',
  geminiEmbeddingModel: 'text-embedding-004',
  geminiMaxTokens: '',
  geminiTemperature: '',
  geminiTimeout: '',
  geminiPriority: '',
  onnxModelAlias: 'all-MiniLM-L6-v2',
  onnxModelPath: '',
  onnxTokenizerPath: '',
  onnxMaxSequenceLength: '512',
  onnxUseGpu: false,
  qdrantHost: '',
  qdrantPort: '6333',
  qdrantGrpcPort: '6334',
  qdrantTimeout: '',
  qdrantPreferGrpc: false,
  qdrantManagedCollectionsEnabled: false,
  qdrantCloudAccountId: '',
  qdrantCloudProviderId: 'aws',
  qdrantCloudRegionId: '',
  qdrantCloudPackageId: '',
  qdrantCloudClusterNameOverride: '',
  restEmbeddingValidateOnStartup: false,
  restEmbeddingBaseUrl: '',
  restEmbeddingEndpoint: '/embed',
  restEmbeddingBatchEndpoint: '/embed/batch',
  restEmbeddingModel: 'all-MiniLM-L6-v2',
  restEmbeddingTimeoutMs: '30000',
  pineconeEnvironment: '',
  pineconeIndexName: '',
  pineconeProjectId: '',
  pineconeApiHost: '',
  pineconeDimensions: '1536',
  pineconeManagedIndexEnabled: false,
  pineconeCloud: 'aws',
  pineconeRegion: 'us-east-1',
  pineconeMetric: 'cosine',
  pineconeDeletionProtectionEnabled: false,
  weaviateScheme: 'https',
  weaviateHost: '',
  weaviatePort: '443',
  weaviateTimeout: '',
  weaviateConsistencyLevelStrong: false,
  milvusHost: '',
  milvusPort: '19530',
  milvusDatabaseName: 'default',
  milvusTimeout: '',
  milvusSecure: false,
  milvusFlushOnWrite: false,
  zillizCloudProjectId: '',
  zillizCloudRegionId: '',
  zillizCloudClusterPlan: 'Serverless',
  zillizCloudCuType: '',
  zillizCloudCuSize: '',
  zillizCloudClusterNameOverride: '',
}

const providerFormKeys: Array<keyof ProviderFormState> = [
  'llmProvider',
  'embeddingProvider',
  'vectorStrategy',
  'vectorProvisioningMode',
  'runtimeProfile',
  'connectorProfile',
  'enableFallback',
  'orchestrationLlmProvider',
  'orchestrationModel',
  'orchestrationTemperature',
  'orchestrationMaxTokens',
  'orchestrationTimeout',
  'generationLlmProvider',
  'generationModel',
  'generationTemperature',
  'generationMaxTokens',
  'generationTimeout',
  'openaiValidateOnStartup',
  'openaiBaseUrl',
  'openaiModel',
  'openaiEmbeddingModel',
  'openaiEmbeddingDimensions',
  'openaiMaxTokens',
  'openaiTemperature',
  'openaiTimeout',
  'openaiPriority',
  'anthropicBaseUrl',
  'anthropicModel',
  'anthropicMaxTokens',
  'anthropicTemperature',
  'anthropicTimeout',
  'anthropicPriority',
  'azureValidateOnStartup',
  'azureEndpoint',
  'azureDeploymentName',
  'azureEmbeddingDeploymentName',
  'azureApiVersion',
  'azureTimeout',
  'azurePriority',
  'cohereValidateOnStartup',
  'cohereBaseUrl',
  'cohereModel',
  'cohereEmbeddingModel',
  'cohereMaxTokens',
  'cohereTemperature',
  'cohereTimeout',
  'coherePriority',
  'geminiValidateOnStartup',
  'geminiBaseUrl',
  'geminiModel',
  'geminiEmbeddingModel',
  'geminiMaxTokens',
  'geminiTemperature',
  'geminiTimeout',
  'geminiPriority',
  'onnxModelAlias',
  'onnxModelPath',
  'onnxTokenizerPath',
  'onnxMaxSequenceLength',
  'onnxUseGpu',
  'qdrantHost',
  'qdrantPort',
  'qdrantGrpcPort',
  'qdrantTimeout',
  'qdrantPreferGrpc',
  'qdrantManagedCollectionsEnabled',
  'qdrantCloudAccountId',
  'qdrantCloudProviderId',
  'qdrantCloudRegionId',
  'qdrantCloudPackageId',
  'qdrantCloudClusterNameOverride',
  'restEmbeddingValidateOnStartup',
  'restEmbeddingBaseUrl',
  'restEmbeddingEndpoint',
  'restEmbeddingBatchEndpoint',
  'restEmbeddingModel',
  'restEmbeddingTimeoutMs',
  'pineconeEnvironment',
  'pineconeIndexName',
  'pineconeProjectId',
  'pineconeApiHost',
  'pineconeDimensions',
  'pineconeManagedIndexEnabled',
  'pineconeCloud',
  'pineconeRegion',
  'pineconeMetric',
  'pineconeDeletionProtectionEnabled',
  'weaviateScheme',
  'weaviateHost',
  'weaviatePort',
  'weaviateTimeout',
  'weaviateConsistencyLevelStrong',
  'milvusHost',
  'milvusPort',
  'milvusDatabaseName',
  'milvusTimeout',
  'milvusSecure',
  'milvusFlushOnWrite',
  'zillizCloudProjectId',
  'zillizCloudRegionId',
  'zillizCloudClusterPlan',
  'zillizCloudCuType',
  'zillizCloudCuSize',
  'zillizCloudClusterNameOverride',
]

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function cloneJson<T>(value: T): T {
  return JSON.parse(JSON.stringify(value ?? null)) as T
}

function readString(config: Record<string, unknown>, key: string, fallback = ''): string {
  const value = config[key]
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'number') {
    return String(value)
  }
  return fallback
}

function readBoolean(config: Record<string, unknown>, key: string, fallback = false): boolean {
  const value = config[key]
  if (typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'string') {
    return value.trim().toLowerCase() === 'true'
  }
  return fallback
}

function selectedLlmProviders(form: ProviderFormState): string[] {
  return Array.from(new Set([
    form.llmProvider.trim(),
    form.orchestrationLlmProvider.trim(),
    form.generationLlmProvider.trim(),
  ].filter(Boolean)))
}

function vectorProvisioningLabel(value: string): string {
  switch (value) {
    case 'LOCAL_MANAGED':
      return 'Local runtime-managed'
    case 'EXTERNAL_EXISTING':
      return 'Bring your own'
    case 'PLATFORM_MANAGED':
      return 'Platform-managed'
    default:
      return value
  }
}

function supportsLocalManagedVector(strategy: string): boolean {
  return strategy === 'lucene' || strategy === 'memory'
}

function supportsExternalExistingVector(strategy: string): boolean {
  return strategy === 'pinecone' || strategy === 'qdrant' || strategy === 'weaviate' || strategy === 'milvus'
}

function supportsPlatformManagedVector(strategy: string): boolean {
  return strategy === 'pinecone' || strategy === 'qdrant' || strategy === 'milvus'
}

function vectorProvisioningOptions(strategy: string): Array<{ value: string; label: string; helper: string }> {
  if (supportsLocalManagedVector(strategy)) {
    return [
      {
        value: 'LOCAL_MANAGED',
        label: 'Local runtime-managed',
        helper: 'This backend is local to the runtime process, so no external vendor infrastructure is provisioned.',
      },
    ]
  }
  if (strategy === 'pinecone') {
    return [
      {
        value: 'PLATFORM_MANAGED',
        label: 'Platform-managed',
        helper: 'The platform creates or reconciles the Pinecone index and binds the resulting host back into the deployment.',
      },
      {
        value: 'EXTERNAL_EXISTING',
        label: 'Bring your own',
        helper: 'Use an existing Pinecone target and keep provider-side ownership outside the platform.',
      },
    ]
  }
  if (strategy === 'qdrant') {
    return [
      {
        value: 'PLATFORM_MANAGED',
        label: 'Platform-managed',
        helper: 'The platform creates or reuses a Qdrant Cloud cluster, issues a deployment-scoped database key, and reconciles one collection per entity type.',
      },
      {
        value: 'EXTERNAL_EXISTING',
        label: 'Bring your own',
        helper: 'Use an existing Qdrant endpoint and keep provider-side ownership outside the platform.',
      },
    ]
  }
  if (strategy === 'milvus') {
    return [
      {
        value: 'PLATFORM_MANAGED',
        label: 'Platform-managed',
        helper: 'The platform creates or reuses a Zilliz Cloud cluster, stores deployment-scoped runtime credentials, and binds the resolved Milvus endpoint back into runtime config.',
      },
      {
        value: 'EXTERNAL_EXISTING',
        label: 'Bring your own',
        helper: 'Use an existing Milvus or Zilliz endpoint and keep provider-side ownership outside the platform.',
      },
    ]
  }
  if (strategy === 'weaviate') {
    return [
      {
        value: 'EXTERNAL_EXISTING',
        label: 'Bring your own',
        helper: 'Use an existing Weaviate Cloud or operator-managed Weaviate cluster and keep provider-side ownership outside the platform.',
      },
    ]
  }
  return []
}

function defaultVectorProvisioningMode(strategy: string, platformManagedDefault = false): string {
  if (supportsLocalManagedVector(strategy)) {
    return 'LOCAL_MANAGED'
  }
  if (strategy === 'pinecone' || strategy === 'qdrant' || strategy === 'milvus') {
    return platformManagedDefault ? 'PLATFORM_MANAGED' : 'EXTERNAL_EXISTING'
  }
  if (supportsExternalExistingVector(strategy)) {
    return 'EXTERNAL_EXISTING'
  }
  return 'LOCAL_MANAGED'
}

function normalizeVectorProvisioningMode(strategy: string, mode: string, platformManagedDefault = false): string {
  const normalized = mode.trim().toUpperCase()
  const supported = vectorProvisioningOptions(strategy).map((option) => option.value)
  return supported.includes(normalized)
    ? normalized
    : defaultVectorProvisioningMode(strategy, platformManagedDefault)
}

function provisioningModeGuidance(strategy: string, mode: string): string {
  const selected = vectorProvisioningOptions(strategy).find((option) => option.value === mode)
  return selected?.helper ?? 'Choose how the selected vector backend should be owned and provisioned.'
}

function provisioningCapabilityMessage(strategy: string): { severity: 'info' | 'warning' | 'success'; message: string } {
  switch (strategy) {
    case 'pinecone':
      return {
        severity: 'success',
        message: 'Pinecone currently supports both bring-your-own and platform-managed provisioning. The platform can create or reconcile the serverless index when platform-managed mode is selected.',
      }
    case 'qdrant':
      return {
        severity: 'success',
        message: 'Qdrant now supports both bring-your-own and platform-managed provisioning. The platform can create or reuse a Qdrant Cloud cluster and issue a deployment-scoped database key automatically.',
      }
    case 'weaviate':
      return {
        severity: 'info',
        message: 'Weaviate Cloud is supported as an external managed service target. The platform verifies connectivity and binds runtime config, but Weaviate cluster lifecycle still stays in the vendor console.',
      }
    case 'milvus':
      return {
        severity: 'success',
        message: 'Milvus now supports both bring-your-own and platform-managed provisioning. The platform can provision or reuse a Zilliz Cloud cluster, bind deployment-scoped runtime credentials, and wire the resolved endpoint back into runtime config.',
      }
    default:
      return {
        severity: 'info',
        message: 'This vector backend is runtime-local, so the platform does not provision an external vector service.',
      }
  }
}

function syncProvisioningMode(form: ProviderFormState, nextMode: string): ProviderFormState {
  const normalizedMode = normalizeVectorProvisioningMode(
    form.vectorStrategy,
    nextMode,
    form.pineconeManagedIndexEnabled,
  )
  return {
    ...form,
    vectorProvisioningMode: normalizedMode,
    pineconeManagedIndexEnabled:
      form.vectorStrategy === 'pinecone'
        ? normalizedMode === 'PLATFORM_MANAGED'
        : form.pineconeManagedIndexEnabled,
    qdrantManagedCollectionsEnabled:
      form.vectorStrategy === 'qdrant'
        ? normalizedMode === 'PLATFORM_MANAGED' || form.qdrantManagedCollectionsEnabled
        : form.qdrantManagedCollectionsEnabled,
    milvusSecure:
      form.vectorStrategy === 'milvus'
        ? normalizedMode === 'PLATFORM_MANAGED' || form.milvusSecure
        : form.milvusSecure,
    milvusPort:
      form.vectorStrategy === 'milvus' && normalizedMode === 'PLATFORM_MANAGED'
        ? '443'
        : form.milvusPort,
  }
}

function syncVectorStrategy(form: ProviderFormState, nextStrategy: string): ProviderFormState {
  const nextMode = normalizeVectorProvisioningMode(
    nextStrategy,
    form.vectorProvisioningMode,
    nextStrategy === 'pinecone' ? form.pineconeManagedIndexEnabled : false,
  )
  return {
    ...form,
    vectorStrategy: nextStrategy,
    vectorProvisioningMode: nextMode,
    pineconeManagedIndexEnabled: nextStrategy === 'pinecone' ? nextMode === 'PLATFORM_MANAGED' : false,
    qdrantManagedCollectionsEnabled:
      nextStrategy === 'qdrant'
        ? nextMode === 'PLATFORM_MANAGED' || form.qdrantManagedCollectionsEnabled
        : false,
    milvusSecure:
      nextStrategy === 'milvus'
        ? nextMode === 'PLATFORM_MANAGED' || form.milvusSecure
        : form.milvusSecure,
    milvusPort:
      nextStrategy === 'milvus' && nextMode === 'PLATFORM_MANAGED'
        ? '443'
        : form.milvusPort,
  }
}

function readEntityTypes(config: unknown): string[] {
  if (!isRecord(config)) {
    return []
  }
  const entities = isRecord(config['ai-entities']) ? config['ai-entities'] : null
  if (!entities) {
    return []
  }
  return Object.keys(entities)
    .map((value) => value.trim())
    .filter(Boolean)
}

function buildManagedVectorDraftSummary(form: ProviderFormState, entityTypes: string[]): ManagedVectorDraftSummary {
  if (form.vectorStrategy === 'pinecone' && form.vectorProvisioningMode === 'PLATFORM_MANAGED') {
    return {
      enabled: true,
      mode: 'MANAGED_SERVERLESS_INDEX',
      targets: [
        `${form.pineconeIndexName.trim() || 'Index name not configured'} (${form.pineconeCloud.trim() || 'aws'}/${form.pineconeRegion.trim() || 'region not configured'})`,
      ],
      message: form.pineconeIndexName.trim()
        ? 'Apply will create or reconcile the Pinecone serverless index, resolve its API host, and bind runtime to the managed resource.'
        : 'Platform-managed Pinecone serverless provisioning is enabled, but pineconeIndexName still needs review before apply.',
    }
  }
  if (form.vectorStrategy === 'qdrant' && form.vectorProvisioningMode === 'PLATFORM_MANAGED') {
    return {
      enabled: true,
      mode: 'MANAGED_CLOUD_CLUSTER',
      targets: [
        `${form.qdrantCloudClusterNameOverride.trim() || 'deployment-managed cluster'} (${form.qdrantCloudProviderId.trim() || 'aws'}/${form.qdrantCloudRegionId.trim() || 'region not configured'})`,
        ...(entityTypes.length > 0 ? entityTypes : ['No entity types configured']),
      ],
      message: form.qdrantCloudRegionId.trim()
        ? 'Apply will create or reuse a Qdrant Cloud cluster, issue a deployment-scoped database key, and reconcile one collection per configured entity type.'
        : 'Platform-managed Qdrant Cloud provisioning is enabled, but qdrantCloudRegionId still needs review before apply.',
    }
  }
  if (form.vectorStrategy === 'milvus' && form.vectorProvisioningMode === 'PLATFORM_MANAGED') {
    return {
      enabled: true,
      mode: 'MANAGED_ZILLIZ_CLOUD_CLUSTER',
      targets: [
        `${form.zillizCloudClusterNameOverride.trim() || 'deployment-managed cluster'} (${form.zillizCloudProjectId.trim() || 'project not configured'} / ${form.zillizCloudRegionId.trim() || 'region not configured'} / ${form.zillizCloudClusterPlan.trim() || 'Serverless'})`,
      ],
      message: form.zillizCloudProjectId.trim() && form.zillizCloudRegionId.trim()
        ? 'Apply will create or reconcile a Zilliz Cloud managed Milvus cluster, store deployment-scoped runtime credentials, and bind the resolved endpoint back into runtime config.'
        : 'Platform-managed Zilliz Cloud provisioning is enabled, but zillizCloudProjectId and zillizCloudRegionId still need review before apply.',
    }
  }
  if (form.vectorStrategy === 'qdrant' && form.qdrantManagedCollectionsEnabled) {
    return {
      enabled: true,
      mode: 'MANAGED_COLLECTIONS',
      targets: entityTypes.length > 0 ? entityTypes : ['No entity types configured'],
      message: entityTypes.length > 0
        ? `Apply will create or reconcile Qdrant collections for the configured entity types${form.qdrantHost.trim() ? ` on ${form.qdrantHost.trim()}` : ''}.`
        : 'Platform-managed Qdrant collections are enabled, but entity types still need review before apply.',
    }
  }
  return {
    enabled: false,
    mode: 'NONE',
    targets: [],
    message: 'Platform-managed external vector provisioning is not enabled for this draft.',
  }
}

function buildVendorSetupGuides(
  form: ProviderFormState,
  entityTypes: string[],
  providerSecrets: DeploymentSecretUsageItemSummary[],
): VendorSetupGuide[] {
  const findSecret = (secretName: string) => providerSecrets.find((secret) => secret.secretName === secretName)
  const guides: VendorSetupGuide[] = []

  if (form.embeddingProvider === 'rest') {
    guides.push({
      key: 'rest-embeddings',
      severity: form.restEmbeddingBaseUrl.trim() ? 'info' : 'warning',
      title: 'REST embedding service onboarding',
      lines: [
        form.restEmbeddingBaseUrl.trim()
          ? `Embedding service base URL: ${form.restEmbeddingBaseUrl.trim()}`
          : 'Set the REST embedding base URL before publish/apply.',
        `Embedding endpoint: ${form.restEmbeddingEndpoint.trim() || '/embed'}`,
        `Batch endpoint: ${form.restEmbeddingBatchEndpoint.trim() || '/embed/batch'}`,
        form.restEmbeddingValidateOnStartup
          ? 'Runtime startup validation is enabled for this embedding service.'
          : 'Runtime startup validation is disabled. Run vendor probes before apply if this endpoint changes.',
      ],
    })
  }

  if (form.vectorStrategy === 'qdrant') {
    const qdrantApiKey = findSecret('QDRANT_API_KEY')
    const qdrantCloudManagementKey = findSecret('QDRANT_CLOUD_MANAGEMENT_API_KEY')
    const host = form.qdrantHost.trim()
    const usingFullUrl = /^https?:\/\//i.test(host)
    guides.push({
      key: 'qdrant',
      severity: form.vectorProvisioningMode === 'PLATFORM_MANAGED'
        ? (!form.qdrantCloudRegionId.trim() || !qdrantCloudManagementKey?.present || entityTypes.length === 0 ? 'warning' : 'info')
        : (!host || (form.qdrantManagedCollectionsEnabled && entityTypes.length === 0) ? 'warning' : 'info'),
      title: form.vectorProvisioningMode === 'PLATFORM_MANAGED' ? 'Qdrant Cloud managed onboarding' : 'Qdrant cluster onboarding',
      lines: form.vectorProvisioningMode === 'PLATFORM_MANAGED'
        ? [
            `Provisioning mode: ${vectorProvisioningLabel(form.vectorProvisioningMode)}`,
            `Qdrant Cloud target: ${form.qdrantCloudProviderId.trim() || 'aws'}/${form.qdrantCloudRegionId.trim() || 'region not configured'}`,
            form.qdrantCloudAccountId.trim()
              ? `Pinned Qdrant Cloud account: ${form.qdrantCloudAccountId.trim()}`
              : 'Account will auto-resolve from the management key when only one Qdrant Cloud account is accessible.',
            form.qdrantCloudPackageId.trim()
              ? `Pinned Qdrant Cloud package: ${form.qdrantCloudPackageId.trim()}`
              : 'Package selection will auto-pick the cheapest active package in the chosen region.',
            qdrantCloudManagementKey?.present
              ? `${qdrantCloudManagementKey.displayName} is available from ${qdrantCloudManagementKey.source.toLowerCase()}.`
              : 'QDRANT_CLOUD_MANAGEMENT_API_KEY is not configured. Add it in Secrets before applying a platform-managed Qdrant deployment.',
            entityTypes.length > 0
              ? `Apply will create or reconcile one Qdrant collection per entity type: ${entityTypes.join(', ')}.`
              : 'Platform-managed Qdrant Cloud provisioning requires at least one configured entity type in Entities.',
          ]
        : [
            `Provisioning mode: ${vectorProvisioningLabel(form.vectorProvisioningMode)}`,
            host
              ? usingFullUrl
                ? `Qdrant target: ${host}. Port fields are ignored when a full URL is supplied.`
                : `Qdrant target: ${host}:${form.qdrantPort.trim() || '6333'} with gRPC ${form.qdrantGrpcPort.trim() || '6334'}.`
              : 'Set qdrantHost to a hostname or full https:// cluster endpoint. For Qdrant Cloud, paste the full cluster URL.',
            qdrantApiKey?.present
              ? `${qdrantApiKey.displayName} is available from ${qdrantApiKey.source.toLowerCase()}.`
              : 'QDRANT_API_KEY is not configured. Add it in Secrets if the target cluster requires authentication.',
            form.qdrantManagedCollectionsEnabled
              ? entityTypes.length > 0
                ? `Apply will create or reconcile one Qdrant collection per entity type: ${entityTypes.join(', ')}.`
                : 'Managed collections are enabled, but no entity types are configured yet in Entities.'
              : 'Managed collection reconciliation is disabled. The platform will use existing Qdrant collections only.',
          ],
    })
  }

  if (form.vectorStrategy === 'pinecone') {
    const pineconeApiKey = findSecret('PINECONE_API_KEY')
    guides.push({
      key: 'pinecone',
      severity: form.vectorProvisioningMode === 'PLATFORM_MANAGED'
        && (!form.pineconeIndexName.trim() || !form.pineconeRegion.trim() || !pineconeApiKey?.present)
        ? 'warning'
        : 'info',
      title: form.vectorProvisioningMode === 'PLATFORM_MANAGED'
        ? 'Pinecone serverless managed onboarding'
        : 'Pinecone bring-your-own onboarding',
      lines: [
        `Provisioning mode: ${vectorProvisioningLabel(form.vectorProvisioningMode)}`,
        form.vectorProvisioningMode === 'PLATFORM_MANAGED'
          ? `Platform-managed serverless index provisioning is enabled for ${form.pineconeIndexName.trim() || 'the pending index name'}.`
          : 'Bring-your-own mode is active. The runtime expects the Pinecone index and host binding to exist already.',
        `Pinecone cloud/region: ${form.pineconeCloud.trim() || 'aws'}/${form.pineconeRegion.trim() || 'region not configured'}`,
        pineconeApiKey?.present
          ? `${pineconeApiKey.displayName} is available from ${pineconeApiKey.source.toLowerCase()}.`
          : 'PINECONE_API_KEY is not configured. Add it in Secrets before using Pinecone in either platform-managed or bring-your-own mode.',
        form.pineconeApiHost.trim()
          ? `Pinned API host: ${form.pineconeApiHost.trim()}`
          : form.vectorProvisioningMode === 'PLATFORM_MANAGED'
            ? 'Leave API host empty. The platform resolves it from the Pinecone control plane during apply.'
            : 'Leave API host empty unless you want to target a fully qualified Pinecone host explicitly.',
      ],
    })
  }

  if (form.vectorStrategy === 'weaviate') {
    const weaviateApiKey = findSecret('WEAVIATE_API_KEY')
    guides.push({
      key: 'weaviate',
      severity: form.weaviateHost.trim() ? 'info' : 'warning',
      title: 'Weaviate Cloud onboarding',
      lines: [
        `Provisioning mode: ${vectorProvisioningLabel(form.vectorProvisioningMode)}`,
        form.weaviateHost.trim()
          ? `Weaviate target: ${form.weaviateScheme.trim() || 'https'}://${form.weaviateHost.trim()}:${form.weaviatePort.trim() || '443'}`
          : 'Set the Weaviate Cloud cluster host before publish/apply.',
        weaviateApiKey?.present
          ? `${weaviateApiKey.displayName} is available from ${weaviateApiKey.source.toLowerCase()}.`
          : 'WEAVIATE_API_KEY is not configured. Add it in Secrets if the cluster is protected.',
        'Weaviate Cloud cluster lifecycle remains vendor-managed today. Create, resize, and rotate cluster-level access in the Weaviate console, then bind the resulting endpoint here.',
      ],
    })
  }

  if (form.vectorStrategy === 'milvus') {
    const milvusUsername = findSecret('MILVUS_USERNAME')
    const milvusPassword = findSecret('MILVUS_PASSWORD')
    const zillizApiKey = findSecret('ZILLIZ_CLOUD_API_KEY')
    guides.push({
      key: 'milvus',
      severity: form.vectorProvisioningMode === 'PLATFORM_MANAGED'
        ? (!form.zillizCloudProjectId.trim() || !form.zillizCloudRegionId.trim() || !zillizApiKey?.present ? 'warning' : 'info')
        : (form.milvusHost.trim() ? 'info' : 'warning'),
      title: form.vectorProvisioningMode === 'PLATFORM_MANAGED'
        ? 'Zilliz Cloud managed onboarding'
        : 'Milvus cluster onboarding',
      lines: form.vectorProvisioningMode === 'PLATFORM_MANAGED'
        ? [
            `Provisioning mode: ${vectorProvisioningLabel(form.vectorProvisioningMode)}`,
            `Zilliz Cloud target: ${form.zillizCloudProjectId.trim() || 'project not configured'} / ${form.zillizCloudRegionId.trim() || 'region not configured'} / ${form.zillizCloudClusterPlan.trim() || 'Serverless'}`,
            form.zillizCloudClusterNameOverride.trim()
              ? `Cluster name override: ${form.zillizCloudClusterNameOverride.trim()}`
              : 'Cluster name will be derived from the deployment id unless you override it.',
            zillizApiKey?.present
              ? `${zillizApiKey.displayName} is available from ${zillizApiKey.source.toLowerCase()}.`
              : 'ZILLIZ_CLOUD_API_KEY is not configured. Add it in Secrets before applying a platform-managed Zilliz Cloud deployment.',
            ['Standard', 'Enterprise'].includes(form.zillizCloudClusterPlan.trim())
              ? `Dedicated plan settings: ${form.zillizCloudCuType.trim() || 'CU type not configured'} / ${form.zillizCloudCuSize.trim() || 'CU size not configured'} CU`
              : 'Free and Serverless plans do not require dedicated CU settings.',
            'Apply will create or reuse the Zilliz Cloud cluster, store deployment-scoped Milvus runtime credentials, and wire the resolved endpoint back into runtime config automatically.',
          ]
        : [
            `Provisioning mode: ${vectorProvisioningLabel(form.vectorProvisioningMode)}`,
            form.milvusHost.trim()
              ? `Milvus target: ${form.milvusSecure ? 'tls' : 'tcp'}://${form.milvusHost.trim()}:${form.milvusPort.trim() || '19530'}`
              : 'Set the Milvus host before publish/apply.',
            milvusUsername?.present && milvusPassword?.present
              ? 'Milvus username and password are both available from the platform secret workspace.'
              : 'MILVUS_USERNAME and MILVUS_PASSWORD are optional. Add them in Secrets when the Milvus deployment requires authentication.',
          ],
    })
  }

  return guides
}

function readProviderForm(config: unknown): ProviderFormState {
  const record = isRecord(config) ? config : {}
  return {
    llmProvider: readString(record, 'llmProvider', DEFAULT_PROVIDER_FORM_STATE.llmProvider),
    embeddingProvider: readString(record, 'embeddingProvider', DEFAULT_PROVIDER_FORM_STATE.embeddingProvider),
    vectorStrategy: readString(record, 'vectorStrategy', DEFAULT_PROVIDER_FORM_STATE.vectorStrategy),
    vectorProvisioningMode: normalizeVectorProvisioningMode(
      readString(record, 'vectorStrategy', DEFAULT_PROVIDER_FORM_STATE.vectorStrategy),
      readString(record, 'vectorProvisioningMode'),
      readBoolean(record, 'pineconeManagedIndexEnabled') || readString(record, 'vectorProvisioningMode').trim().toUpperCase() === 'PLATFORM_MANAGED',
    ),
    runtimeProfile: readString(record, 'runtimeProfile', DEFAULT_PROVIDER_FORM_STATE.runtimeProfile),
    connectorProfile: readString(record, 'connectorProfile', DEFAULT_PROVIDER_FORM_STATE.connectorProfile),
    enableFallback: readBoolean(record, 'enableFallback', DEFAULT_PROVIDER_FORM_STATE.enableFallback),
    orchestrationLlmProvider: readString(record, 'orchestrationLlmProvider'),
    orchestrationModel: readString(record, 'orchestrationModel'),
    orchestrationTemperature: readString(record, 'orchestrationTemperature'),
    orchestrationMaxTokens: readString(record, 'orchestrationMaxTokens'),
    orchestrationTimeout: readString(record, 'orchestrationTimeout'),
    generationLlmProvider: readString(record, 'generationLlmProvider'),
    generationModel: readString(record, 'generationModel'),
    generationTemperature: readString(record, 'generationTemperature'),
    generationMaxTokens: readString(record, 'generationMaxTokens'),
    generationTimeout: readString(record, 'generationTimeout'),
    openaiValidateOnStartup: readBoolean(record, 'openaiValidateOnStartup'),
    openaiBaseUrl: readString(record, 'openaiBaseUrl'),
    openaiModel: readString(record, 'openaiModel', DEFAULT_PROVIDER_FORM_STATE.openaiModel),
    openaiEmbeddingModel: readString(record, 'openaiEmbeddingModel', DEFAULT_PROVIDER_FORM_STATE.openaiEmbeddingModel),
    openaiEmbeddingDimensions: readString(record, 'openaiEmbeddingDimensions', DEFAULT_PROVIDER_FORM_STATE.openaiEmbeddingDimensions),
    openaiMaxTokens: readString(record, 'openaiMaxTokens'),
    openaiTemperature: readString(record, 'openaiTemperature'),
    openaiTimeout: readString(record, 'openaiTimeout'),
    openaiPriority: readString(record, 'openaiPriority'),
    anthropicBaseUrl: readString(record, 'anthropicBaseUrl'),
    anthropicModel: readString(record, 'anthropicModel', DEFAULT_PROVIDER_FORM_STATE.anthropicModel),
    anthropicMaxTokens: readString(record, 'anthropicMaxTokens'),
    anthropicTemperature: readString(record, 'anthropicTemperature'),
    anthropicTimeout: readString(record, 'anthropicTimeout'),
    anthropicPriority: readString(record, 'anthropicPriority'),
    azureValidateOnStartup: readBoolean(record, 'azureValidateOnStartup'),
    azureEndpoint: readString(record, 'azureEndpoint'),
    azureDeploymentName: readString(record, 'azureDeploymentName'),
    azureEmbeddingDeploymentName: readString(record, 'azureEmbeddingDeploymentName'),
    azureApiVersion: readString(record, 'azureApiVersion', DEFAULT_PROVIDER_FORM_STATE.azureApiVersion),
    azureTimeout: readString(record, 'azureTimeout'),
    azurePriority: readString(record, 'azurePriority'),
    cohereValidateOnStartup: readBoolean(record, 'cohereValidateOnStartup'),
    cohereBaseUrl: readString(record, 'cohereBaseUrl'),
    cohereModel: readString(record, 'cohereModel', DEFAULT_PROVIDER_FORM_STATE.cohereModel),
    cohereEmbeddingModel: readString(record, 'cohereEmbeddingModel', DEFAULT_PROVIDER_FORM_STATE.cohereEmbeddingModel),
    cohereMaxTokens: readString(record, 'cohereMaxTokens'),
    cohereTemperature: readString(record, 'cohereTemperature'),
    cohereTimeout: readString(record, 'cohereTimeout'),
    coherePriority: readString(record, 'coherePriority'),
    geminiValidateOnStartup: readBoolean(record, 'geminiValidateOnStartup'),
    geminiBaseUrl: readString(record, 'geminiBaseUrl'),
    geminiModel: readString(record, 'geminiModel', DEFAULT_PROVIDER_FORM_STATE.geminiModel),
    geminiEmbeddingModel: readString(record, 'geminiEmbeddingModel', DEFAULT_PROVIDER_FORM_STATE.geminiEmbeddingModel),
    geminiMaxTokens: readString(record, 'geminiMaxTokens'),
    geminiTemperature: readString(record, 'geminiTemperature'),
    geminiTimeout: readString(record, 'geminiTimeout'),
    geminiPriority: readString(record, 'geminiPriority'),
    onnxModelAlias: readString(record, 'onnxModelAlias', DEFAULT_PROVIDER_FORM_STATE.onnxModelAlias),
    onnxModelPath: readString(record, 'onnxModelPath'),
    onnxTokenizerPath: readString(record, 'onnxTokenizerPath'),
    onnxMaxSequenceLength: readString(record, 'onnxMaxSequenceLength', DEFAULT_PROVIDER_FORM_STATE.onnxMaxSequenceLength),
    onnxUseGpu: readBoolean(record, 'onnxUseGpu'),
    qdrantHost: readString(record, 'qdrantHost'),
    qdrantPort: readString(record, 'qdrantPort', DEFAULT_PROVIDER_FORM_STATE.qdrantPort),
    qdrantGrpcPort: readString(record, 'qdrantGrpcPort', DEFAULT_PROVIDER_FORM_STATE.qdrantGrpcPort),
    qdrantTimeout: readString(record, 'qdrantTimeout'),
    qdrantPreferGrpc: readBoolean(record, 'qdrantPreferGrpc'),
    qdrantManagedCollectionsEnabled: readBoolean(record, 'qdrantManagedCollectionsEnabled'),
    qdrantCloudAccountId: readString(record, 'qdrantCloudAccountId'),
    qdrantCloudProviderId: readString(record, 'qdrantCloudProviderId', DEFAULT_PROVIDER_FORM_STATE.qdrantCloudProviderId),
    qdrantCloudRegionId: readString(record, 'qdrantCloudRegionId'),
    qdrantCloudPackageId: readString(record, 'qdrantCloudPackageId'),
    qdrantCloudClusterNameOverride: readString(record, 'qdrantCloudClusterNameOverride'),
    restEmbeddingValidateOnStartup: readBoolean(record, 'restEmbeddingValidateOnStartup'),
    restEmbeddingBaseUrl: readString(record, 'restEmbeddingBaseUrl'),
    restEmbeddingEndpoint: readString(record, 'restEmbeddingEndpoint', DEFAULT_PROVIDER_FORM_STATE.restEmbeddingEndpoint),
    restEmbeddingBatchEndpoint: readString(record, 'restEmbeddingBatchEndpoint', DEFAULT_PROVIDER_FORM_STATE.restEmbeddingBatchEndpoint),
    restEmbeddingModel: readString(record, 'restEmbeddingModel', DEFAULT_PROVIDER_FORM_STATE.restEmbeddingModel),
    restEmbeddingTimeoutMs: readString(record, 'restEmbeddingTimeoutMs', DEFAULT_PROVIDER_FORM_STATE.restEmbeddingTimeoutMs),
    pineconeEnvironment: readString(record, 'pineconeEnvironment'),
    pineconeIndexName: readString(record, 'pineconeIndexName'),
    pineconeProjectId: readString(record, 'pineconeProjectId'),
    pineconeApiHost: readString(record, 'pineconeApiHost'),
    pineconeDimensions: readString(record, 'pineconeDimensions', DEFAULT_PROVIDER_FORM_STATE.pineconeDimensions),
    pineconeManagedIndexEnabled:
      readString(record, 'vectorProvisioningMode').trim().toUpperCase() === 'PLATFORM_MANAGED'
      || readBoolean(record, 'pineconeManagedIndexEnabled'),
    pineconeCloud: readString(record, 'pineconeCloud', DEFAULT_PROVIDER_FORM_STATE.pineconeCloud),
    pineconeRegion: readString(record, 'pineconeRegion', DEFAULT_PROVIDER_FORM_STATE.pineconeRegion),
    pineconeMetric: readString(record, 'pineconeMetric', DEFAULT_PROVIDER_FORM_STATE.pineconeMetric),
    pineconeDeletionProtectionEnabled: readBoolean(record, 'pineconeDeletionProtectionEnabled'),
    weaviateScheme: readString(record, 'weaviateScheme', DEFAULT_PROVIDER_FORM_STATE.weaviateScheme),
    weaviateHost: readString(record, 'weaviateHost'),
    weaviatePort: readString(record, 'weaviatePort', DEFAULT_PROVIDER_FORM_STATE.weaviatePort),
    weaviateTimeout: readString(record, 'weaviateTimeout'),
    weaviateConsistencyLevelStrong: readBoolean(record, 'weaviateConsistencyLevelStrong'),
    milvusHost: readString(record, 'milvusHost'),
    milvusPort: readString(
      record,
      'milvusPort',
      readString(record, 'vectorProvisioningMode').trim().toUpperCase() === 'PLATFORM_MANAGED' ? '443' : DEFAULT_PROVIDER_FORM_STATE.milvusPort,
    ),
    milvusDatabaseName: readString(record, 'milvusDatabaseName', DEFAULT_PROVIDER_FORM_STATE.milvusDatabaseName),
    milvusTimeout: readString(record, 'milvusTimeout'),
    milvusSecure:
      readString(record, 'vectorProvisioningMode').trim().toUpperCase() === 'PLATFORM_MANAGED'
      || readBoolean(record, 'milvusSecure'),
    milvusFlushOnWrite: readBoolean(record, 'milvusFlushOnWrite'),
    zillizCloudProjectId: readString(record, 'zillizCloudProjectId'),
    zillizCloudRegionId: readString(record, 'zillizCloudRegionId'),
    zillizCloudClusterPlan: readString(record, 'zillizCloudClusterPlan', DEFAULT_PROVIDER_FORM_STATE.zillizCloudClusterPlan),
    zillizCloudCuType: readString(record, 'zillizCloudCuType'),
    zillizCloudCuSize: readString(record, 'zillizCloudCuSize'),
    zillizCloudClusterNameOverride: readString(record, 'zillizCloudClusterNameOverride'),
  }
}

function buildSummaryItems(form: ProviderFormState): SummaryItem[] {
  const llmProviders = selectedLlmProviders(form)
  const usesOpenAi = llmProviders.includes('openai') || form.embeddingProvider === 'openai'
  const usesAzure = llmProviders.includes('azure') || form.embeddingProvider === 'azure'
  const usesAnthropic = llmProviders.includes('anthropic')
  const usesCohere = llmProviders.includes('cohere') || form.embeddingProvider === 'cohere'
  const usesGemini = llmProviders.includes('gemini') || form.embeddingProvider === 'gemini'
  const items: SummaryItem[] = [
    { label: 'LLM provider', value: form.llmProvider.trim() || 'Not configured' },
    { label: 'Embedding provider', value: form.embeddingProvider.trim() || 'Not configured' },
    { label: 'Vector strategy', value: form.vectorStrategy.trim() || 'Not configured' },
    { label: 'Vector provisioning mode', value: form.vectorProvisioningMode.trim() || 'Not configured' },
    { label: 'Runtime profile', value: form.runtimeProfile.trim() || 'Not configured' },
    { label: 'Connector profile', value: form.connectorProfile.trim() || 'Not configured' },
    { label: 'Fallback enabled', value: String(form.enableFallback) },
  ]

  if (form.orchestrationLlmProvider.trim()) {
    items.push({ label: 'Orchestration provider', value: form.orchestrationLlmProvider.trim() })
  }
  if (form.generationLlmProvider.trim()) {
    items.push({ label: 'Generation provider', value: form.generationLlmProvider.trim() })
  }
  if (usesAzure) {
    items.push({ label: 'Azure endpoint', value: form.azureEndpoint.trim() || 'Not configured' })
    items.push({ label: 'Azure API version', value: form.azureApiVersion.trim() || 'Not configured' })
  }
  if (usesOpenAi) {
    items.push({ label: 'OpenAI base URL', value: form.openaiBaseUrl.trim() || 'Default provider endpoint' })
    items.push({ label: 'OpenAI LLM model', value: form.openaiModel.trim() || 'Default' })
    items.push({ label: 'OpenAI embedding model', value: form.openaiEmbeddingModel.trim() || 'Default' })
    items.push({ label: 'OpenAI embedding dimensions', value: form.openaiEmbeddingDimensions.trim() || '1536' })
  }
  if (usesAnthropic) {
    items.push({ label: 'Anthropic base URL', value: form.anthropicBaseUrl.trim() || 'Default provider endpoint' })
    items.push({ label: 'Anthropic model', value: form.anthropicModel.trim() || 'Default' })
  }
  if (llmProviders.includes('azure')) {
    items.push({ label: 'Azure LLM deployment', value: form.azureDeploymentName.trim() || 'Not configured' })
  }
  if (form.embeddingProvider === 'azure') {
    items.push({ label: 'Azure embedding deployment', value: form.azureEmbeddingDeploymentName.trim() || 'Not configured' })
  }
  if (usesCohere) {
    items.push({ label: 'Cohere base URL', value: form.cohereBaseUrl.trim() || 'Default provider endpoint' })
    items.push({ label: 'Cohere LLM model', value: form.cohereModel.trim() || 'Default' })
    items.push({ label: 'Cohere embedding model', value: form.cohereEmbeddingModel.trim() || 'Default' })
  }
  if (usesGemini) {
    items.push({ label: 'Gemini base URL', value: form.geminiBaseUrl.trim() || 'Default provider endpoint' })
    items.push({ label: 'Gemini LLM model', value: form.geminiModel.trim() || 'Default' })
    items.push({ label: 'Gemini embedding model', value: form.geminiEmbeddingModel.trim() || 'Default' })
  }
  if (form.embeddingProvider === 'onnx') {
    items.push({ label: 'ONNX model alias', value: form.onnxModelAlias.trim() || 'all-MiniLM-L6-v2' })
    items.push({ label: 'ONNX max sequence length', value: form.onnxMaxSequenceLength.trim() || '512' })
    items.push({ label: 'ONNX GPU acceleration', value: String(form.onnxUseGpu) })
  }
  if (form.embeddingProvider === 'rest') {
    items.push({ label: 'REST embedding base URL', value: form.restEmbeddingBaseUrl.trim() || 'Not configured' })
    items.push({ label: 'REST embedding endpoint', value: form.restEmbeddingEndpoint.trim() || '/embed' })
    items.push({ label: 'REST timeout (ms)', value: form.restEmbeddingTimeoutMs.trim() || '30000' })
  }
  if (form.vectorStrategy === 'qdrant') {
    items.push({ label: 'Qdrant provisioning', value: form.vectorProvisioningMode.trim() || 'EXTERNAL_EXISTING' })
    if (form.vectorProvisioningMode === 'PLATFORM_MANAGED') {
      items.push({ label: 'Qdrant Cloud provider', value: form.qdrantCloudProviderId.trim() || 'aws' })
      items.push({ label: 'Qdrant Cloud region', value: form.qdrantCloudRegionId.trim() || 'Not configured' })
      items.push({ label: 'Qdrant Cloud account', value: form.qdrantCloudAccountId.trim() || 'Auto-resolve' })
      items.push({ label: 'Qdrant Cloud package', value: form.qdrantCloudPackageId.trim() || 'Auto-select' })
      items.push({ label: 'Qdrant cluster name', value: form.qdrantCloudClusterNameOverride.trim() || 'Derived from deployment id' })
    } else {
      items.push({ label: 'Qdrant host', value: form.qdrantHost.trim() || 'Not configured' })
      items.push({ label: 'Qdrant port', value: form.qdrantPort.trim() || '6333' })
      items.push({ label: 'Qdrant gRPC port', value: form.qdrantGrpcPort.trim() || '6334' })
    }
    items.push({ label: 'Qdrant timeout (s)', value: form.qdrantTimeout.trim() || 'Default 30' })
    items.push({ label: 'Prefer gRPC', value: String(form.qdrantPreferGrpc) })
    items.push({ label: 'Platform-managed collections', value: String(form.vectorProvisioningMode === 'PLATFORM_MANAGED' || form.qdrantManagedCollectionsEnabled) })
  }
  if (form.vectorStrategy === 'pinecone') {
    items.push({ label: 'Pinecone provisioning', value: form.vectorProvisioningMode.trim() || 'Not configured' })
    items.push({ label: 'Pinecone index', value: form.pineconeIndexName.trim() || 'Derived from API host or not configured' })
    items.push({ label: 'Pinecone dimensions', value: form.pineconeDimensions.trim() || '1536' })
    items.push({ label: 'Pinecone cloud', value: form.pineconeCloud.trim() || 'aws' })
    items.push({ label: 'Pinecone region', value: form.pineconeRegion.trim() || 'us-east-1' })
    items.push({ label: 'Pinecone metric', value: form.pineconeMetric.trim() || 'cosine' })
    items.push({ label: 'Deletion protection', value: String(form.pineconeDeletionProtectionEnabled) })
    if (form.vectorProvisioningMode === 'PLATFORM_MANAGED') {
      items.push({ label: 'Pinecone API host', value: form.pineconeApiHost.trim() || 'Resolved during apply' })
    } else {
      items.push({ label: 'Pinecone environment', value: form.pineconeEnvironment.trim() || 'Not configured' })
      items.push({ label: 'Pinecone project ID', value: form.pineconeProjectId.trim() || 'Not configured' })
      items.push({ label: 'Pinecone API host', value: form.pineconeApiHost.trim() || 'Not configured' })
    }
  }
  if (form.vectorStrategy === 'weaviate') {
    items.push({ label: 'Weaviate provisioning', value: form.vectorProvisioningMode.trim() || 'EXTERNAL_EXISTING' })
    items.push({ label: 'Weaviate scheme', value: form.weaviateScheme.trim() || 'https' })
    items.push({ label: 'Weaviate host', value: form.weaviateHost.trim() || 'Not configured' })
    items.push({ label: 'Weaviate port', value: form.weaviatePort.trim() || '443' })
    items.push({ label: 'Weaviate timeout (s)', value: form.weaviateTimeout.trim() || 'Default 30' })
    items.push({ label: 'Strong consistency', value: String(form.weaviateConsistencyLevelStrong) })
  }
  if (form.vectorStrategy === 'milvus') {
    items.push({ label: 'Milvus provisioning', value: form.vectorProvisioningMode.trim() || 'EXTERNAL_EXISTING' })
    if (form.vectorProvisioningMode === 'PLATFORM_MANAGED') {
      items.push({ label: 'Zilliz project ID', value: form.zillizCloudProjectId.trim() || 'Not configured' })
      items.push({ label: 'Zilliz region ID', value: form.zillizCloudRegionId.trim() || 'Not configured' })
      items.push({ label: 'Zilliz plan', value: form.zillizCloudClusterPlan.trim() || 'Serverless' })
      items.push({ label: 'Zilliz cluster name', value: form.zillizCloudClusterNameOverride.trim() || 'Derived from deployment id' })
      if (['Standard', 'Enterprise'].includes(form.zillizCloudClusterPlan.trim())) {
        items.push({ label: 'Zilliz CU type', value: form.zillizCloudCuType.trim() || 'Not configured' })
        items.push({ label: 'Zilliz CU size', value: form.zillizCloudCuSize.trim() || 'Not configured' })
      }
      items.push({ label: 'Resolved Milvus host', value: form.milvusHost.trim() || 'Resolved during apply' })
      items.push({ label: 'Resolved Milvus port', value: form.milvusPort.trim() || '443' })
    } else {
      items.push({ label: 'Milvus host', value: form.milvusHost.trim() || 'Not configured' })
      items.push({ label: 'Milvus port', value: form.milvusPort.trim() || '19530' })
    }
    items.push({ label: 'Milvus database', value: form.milvusDatabaseName.trim() || 'default' })
    items.push({ label: 'Milvus timeout (s)', value: form.milvusTimeout.trim() || 'Default 30' })
    items.push({ label: 'Secure transport', value: String(form.milvusSecure) })
    items.push({ label: 'Flush on write', value: String(form.milvusFlushOnWrite) })
  }

  return items
}

function providerFormsEqual(left: ProviderFormState, right: ProviderFormState): boolean {
  return providerFormKeys.every((key) => left[key] === right[key])
}

function buildProviderConfigDraft(baseConfig: unknown, form: ProviderFormState): Record<string, unknown> {
  const nextConfig = cloneJson(isRecord(baseConfig) ? baseConfig : {}) as Record<string, unknown>
  providerFormKeys.forEach((key) => {
    const value = form[key]
    nextConfig[key] = typeof value === 'string' ? value.trim() : value
  })
  if (form.vectorStrategy === 'qdrant' && form.vectorProvisioningMode === 'PLATFORM_MANAGED') {
    nextConfig.qdrantManagedCollectionsEnabled = true
    nextConfig.qdrantHost = ''
  }
  if (form.vectorStrategy === 'qdrant' && form.vectorProvisioningMode !== 'PLATFORM_MANAGED') {
    nextConfig.qdrantCloudAccountId = ''
    nextConfig.qdrantCloudRegionId = ''
    nextConfig.qdrantCloudPackageId = ''
    nextConfig.qdrantCloudClusterNameOverride = ''
  }
  if (form.vectorStrategy === 'pinecone' && form.vectorProvisioningMode === 'PLATFORM_MANAGED') {
    nextConfig.pineconeManagedIndexEnabled = true
    nextConfig.pineconeEnvironment = ''
    nextConfig.pineconeProjectId = ''
    nextConfig.pineconeApiHost = ''
  }
  if (form.vectorStrategy === 'pinecone' && form.vectorProvisioningMode !== 'PLATFORM_MANAGED') {
    nextConfig.pineconeManagedIndexEnabled = false
  }
  if (form.vectorStrategy === 'milvus' && form.vectorProvisioningMode === 'PLATFORM_MANAGED') {
    nextConfig.milvusHost = ''
    nextConfig.milvusPort = '443'
    nextConfig.milvusSecure = true
  }
  if (form.vectorStrategy === 'milvus' && form.vectorProvisioningMode !== 'PLATFORM_MANAGED') {
    nextConfig.zillizCloudProjectId = ''
    nextConfig.zillizCloudRegionId = ''
    nextConfig.zillizCloudClusterPlan = 'Serverless'
    nextConfig.zillizCloudCuType = ''
    nextConfig.zillizCloudCuSize = ''
    nextConfig.zillizCloudClusterNameOverride = ''
  }
  return nextConfig
}

function chipColorForStatus(status: string): 'default' | 'error' | 'info' | 'success' | 'warning' {
  if (status === 'READY' || status === 'PASSED') {
    return 'success'
  }
  if (status === 'BLOCKED' || status === 'FAILED' || status === 'ERROR' || status === 'MISSING') {
    return 'error'
  }
  if (status === 'WARNING') {
    return 'warning'
  }
  if (status === 'SKIPPED') {
    return 'default'
  }
  return 'info'
}

export function ProvidersPage() {
  const { selectedDeploymentId, workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const [formState, setFormState] = useState<ProviderFormState>(DEFAULT_PROVIDER_FORM_STATE)
  const canEdit = workspace?.access.canEdit ?? false

  const draftQuery = useQuery({
    queryKey: ['deployment-draft', selectedDeploymentId],
    queryFn: () => fetchDeploymentDraft(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  useEffect(() => {
    if (draftQuery.data) {
      setFormState(readProviderForm(draftQuery.data.providerConfig))
    }
  }, [draftQuery.data])

  const summaryItems = useMemo(() => buildSummaryItems(formState), [formState])
  const entityTypes = useMemo(() => readEntityTypes(draftQuery.data?.entityConfig), [draftQuery.data?.entityConfig])
  const managedVectorDraftSummary = useMemo(
    () => buildManagedVectorDraftSummary(formState, entityTypes),
    [entityTypes, formState],
  )
  const selectedLlmProviderSet = useMemo(() => new Set(selectedLlmProviders(formState)), [formState])
  const usesOpenAiProvider = selectedLlmProviderSet.has('openai') || formState.embeddingProvider === 'openai'
  const usesAzureProvider = selectedLlmProviderSet.has('azure') || formState.embeddingProvider === 'azure'
  const usesAnthropicProvider = selectedLlmProviderSet.has('anthropic')
  const usesCohereProvider = selectedLlmProviderSet.has('cohere') || formState.embeddingProvider === 'cohere'
  const usesGeminiProvider = selectedLlmProviderSet.has('gemini') || formState.embeddingProvider === 'gemini'
  const savedFormState = useMemo(
    () => readProviderForm(draftQuery.data?.providerConfig),
    [draftQuery.data?.providerConfig],
  )
  const draftDirty = useMemo(
    () => (draftQuery.data ? !providerFormsEqual(formState, savedFormState) : false),
    [draftQuery.data, formState, savedFormState],
  )
  const editorState = useMemo(
    () => ({
      dirty: draftDirty,
      label: 'Provider config',
      description: draftDirty
        ? 'Provider profile edits exist only in the current browser buffer until you save the deployment draft.'
        : 'Provider profile editor matches the saved deployment draft.',
    }),
    [draftDirty],
  )
  useDeploymentWorkspaceEditorState(selectedDeploymentId ? editorState : null)

  const secretUsageQuery = useQuery({
    queryKey: ['deployment-secret-usage', selectedDeploymentId],
    queryFn: () => fetchDeploymentSecretUsage(selectedDeploymentId),
    enabled: selectedDeploymentId.length > 0,
  })

  const validationQuery = useQuery({
    queryKey: ['deployment-validation', draftQuery.data?.id],
    queryFn: () => validateDeploymentDraft(draftQuery.data!.id),
    enabled: Boolean(draftQuery.data?.id),
  })

  const connectivityMutation = useMutation({
    mutationFn: (providerConfig?: unknown) => (
      providerConfig != null
        ? probeDeploymentProviderConnectivity(selectedDeploymentId, { providerConfig })
        : fetchDeploymentProviderConnectivity(selectedDeploymentId)
    ),
  })

  useEffect(() => {
    connectivityMutation.reset()
  }, [selectedDeploymentId, draftQuery.data?.id])

  const providerValidationIssues = useMemo(
    () => validationQuery.data?.issues.filter((issue) => issue.section === 'providers') ?? [],
    [validationQuery.data],
  )
  const providerSecrets = useMemo(
    () => secretUsageQuery.data?.secrets.filter((secret) =>
      secret.usedByServices.includes('Provider stack') || secret.usedByServices.includes('Vector database'),
    ) ?? [],
    [secretUsageQuery.data],
  )
  const providerSetupGuides = useMemo(
    () => buildVendorSetupGuides(formState, entityTypes, providerSecrets),
    [entityTypes, formState, providerSecrets],
  )

  const saveMutation = useMutation({
    mutationFn: ({ draftId, providerConfig }: { draftId: string; providerConfig: unknown }) =>
      updateDeploymentDraft(draftId, { providerConfig }),
    onSuccess: async (_data, variables) => {
      connectivityMutation.reset()
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation', variables.draftId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-secret-usage', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
      ])
    },
  })

  const handleFieldChange = <K extends keyof ProviderFormState>(key: K, value: ProviderFormState[K]) => {
    setFormState((previous) => {
      if (key === 'vectorStrategy') {
        return syncVectorStrategy(previous, String(value))
      }
      if (key === 'vectorProvisioningMode') {
        return syncProvisioningMode(previous, String(value))
      }
      return {
        ...previous,
        [key]: value,
      }
    })
  }

  const handleSave = () => {
    if (!draftQuery.data) {
      return
    }

    const nextConfig = buildProviderConfigDraft(draftQuery.data.providerConfig, formState)

    saveMutation.mutate({
      draftId: draftQuery.data.id,
      providerConfig: nextConfig,
    })
  }

  const handleRunVendorProbes = () => {
    if (!selectedDeploymentId) {
      return
    }
    if (draftDirty && canEdit && draftQuery.data) {
      connectivityMutation.mutate(buildProviderConfigDraft(draftQuery.data.providerConfig, formState))
      return
    }
    connectivityMutation.mutate(undefined)
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Chip label="Providers" color="primary" sx={{ mb: 1.5, fontWeight: 700 }} />
        <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: -0.8 }}>
          Provider profile editor
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 980 }}>
          Provider selection stays structured and versioned. This screen edits the bounded provider knobs
          that later compile into runtime deployment settings without storing credentials in the draft.
        </Typography>
      </Box>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Deployment workspace</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Provider profile changes follow the deployment selected in the shared workspace header.
              </Typography>
            </Box>

            {workspace ? (
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={workspace.deployment.name} variant="outlined" />
                <Chip label={workspace.deployment.environment} variant="outlined" />
                <Chip label={workspace.deployment.status} color="primary" />
                <Chip label={workspace.template.name} variant="outlined" />
              </Stack>
            ) : null}
            {!canEdit && workspace ? (
              <Alert severity="info">
                Saving provider config requires deployment editor access or higher.
              </Alert>
            ) : null}

            {draftQuery.data ? (
              <Stack direction="row" spacing={1} flexWrap="wrap">
                <Chip label={`Draft: ${draftQuery.data.id}`} variant="outlined" />
                <Chip label={`Revision ${draftQuery.data.revisionNumber}`} variant="outlined" />
                <Chip label={draftQuery.data.status} color="primary" />
              </Stack>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

      <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
        <CardContent>
          <Stack spacing={2}>
            <Box>
              <Typography variant="h6">Provider readiness</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                These checks run from the saved deployment draft. Save changes before treating validation,
                secret readiness, or vendor probes as authoritative.
              </Typography>
            </Box>

            {draftDirty ? (
              <Alert severity="info">
                Provider checks below still reflect the saved draft. Save the current browser edits to re-evaluate
                provider validation and secret readiness.
              </Alert>
            ) : null}

            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <Stack spacing={1.5}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Saved draft validation
                    </Typography>
                    {validationQuery.data ? (
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip
                          label={validationQuery.data.publishReady ? 'Publish ready' : 'Blocked'}
                          color={validationQuery.data.publishReady ? 'success' : 'error'}
                          size="small"
                        />
                        <Chip label={`${validationQuery.data.errorCount} errors`} size="small" variant="outlined" />
                        <Chip label={`${validationQuery.data.warningCount} warnings`} size="small" variant="outlined" />
                      </Stack>
                    ) : null}
                  </Box>
                  {validationQuery.isLoading ? (
                    <Typography color="text.secondary">Checking saved provider config…</Typography>
                  ) : validationQuery.isError ? (
                    <Alert severity="error">
                      {validationQuery.error instanceof Error
                        ? validationQuery.error.message
                        : 'Failed to validate the saved deployment draft'}
                    </Alert>
                  ) : providerValidationIssues.length > 0 ? (
                    <List dense disablePadding>
                      {providerValidationIssues.map((issue) => (
                        <ListItem key={`${issue.code}-${issue.path}`} disableGutters>
                          <ListItemText
                            primary={issue.message}
                            secondary={`${issue.code} · ${issue.path}`}
                          />
                        </ListItem>
                      ))}
                    </List>
                  ) : (
                    <Alert severity="success">
                      No provider-specific validation blockers were found in the saved deployment draft.
                    </Alert>
                  )}
                </Stack>
              </Grid>

              <Grid item xs={12} md={6}>
                <Stack spacing={1.5}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                      Secret readiness
                    </Typography>
                    {secretUsageQuery.data ? (
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip label={`${providerSecrets.length} provider secret reference${providerSecrets.length === 1 ? '' : 's'}`} size="small" variant="outlined" />
                        <Chip
                          label={`${providerSecrets.filter((secret) => secret.required && !secret.present).length} required missing`}
                          size="small"
                          color={providerSecrets.some((secret) => secret.required && !secret.present) ? 'error' : 'success'}
                        />
                      </Stack>
                    ) : null}
                  </Box>
                  {secretUsageQuery.isLoading ? (
                    <Typography color="text.secondary">Checking provider secret usage…</Typography>
                  ) : secretUsageQuery.isError ? (
                    <Alert severity="error">
                      {secretUsageQuery.error instanceof Error
                        ? secretUsageQuery.error.message
                        : 'Failed to load provider secret readiness'}
                    </Alert>
                  ) : providerSecrets.length > 0 ? (
                    <List dense disablePadding>
                      {providerSecrets.map((secret) => (
                        <ListItem key={secret.secretName} disableGutters>
                          <ListItemText
                            primary={secret.displayName}
                            secondary={`${secret.status} · ${secret.usedByServices.join(', ')} · ${secret.summaryMessage}`}
                          />
                        </ListItem>
                      ))}
                    </List>
                  ) : (
                    <Alert severity="info">
                      The current provider stack does not add any managed platform secret dependencies beyond the standard deployment defaults.
                    </Alert>
                  )}
                </Stack>
              </Grid>
            </Grid>

            <Divider />

            <Stack spacing={1.5}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                <Box>
                  <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
                    External vendor probes
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Run on-demand probes for the saved draft to verify Pinecone, Qdrant, Weaviate, or REST embedding reachability before apply.
                  </Typography>
                </Box>
                <Button
                  variant="outlined"
                  onClick={handleRunVendorProbes}
                  disabled={!selectedDeploymentId || connectivityMutation.isPending}
                >
                  {connectivityMutation.isPending
                    ? 'Running probes…'
                    : draftDirty && canEdit
                      ? 'Probe current edits'
                      : 'Run vendor probes'}
                </Button>
              </Box>
              {draftDirty && canEdit ? (
                <Typography variant="body2" color="text.secondary">
                  Unsaved provider edits are present. Probing will use the current browser buffer without saving the draft first.
                </Typography>
              ) : null}

              {(
                connectivityMutation.data?.managedVectorProvisioningEnabled
                || managedVectorDraftSummary.enabled
              ) ? (
                <Alert severity="info">
                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                    {
                      connectivityMutation.data?.managedVectorSummaryMessage
                      ?? managedVectorDraftSummary.message
                    }
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    Mode: {
                      connectivityMutation.data?.managedVectorProvisioningMode
                      ?? managedVectorDraftSummary.mode
                    }
                  </Typography>
                  {(
                    connectivityMutation.data?.managedVectorTargets.length
                    ?? managedVectorDraftSummary.targets.length
                  ) > 0 ? (
                    <List dense disablePadding sx={{ mt: 0.5 }}>
                      {(connectivityMutation.data?.managedVectorTargets ?? managedVectorDraftSummary.targets).map((target) => (
                        <ListItem key={target} disableGutters>
                          <ListItemText primary={target} />
                        </ListItem>
                      ))}
                    </List>
                  ) : null}
                </Alert>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  {managedVectorDraftSummary.message}
                </Typography>
              )}

              {connectivityMutation.isError ? (
                <Alert severity="error">
                  {connectivityMutation.error instanceof Error
                    ? connectivityMutation.error.message
                    : 'Failed to run provider connectivity probes'}
                </Alert>
              ) : null}

              {connectivityMutation.data ? (
                <Stack spacing={1.25}>
                  <Alert severity={
                    connectivityMutation.data.probes.some((probe) => probe.status === 'FAILED' || probe.status === 'BLOCKED')
                      ? 'warning'
                      : 'success'
                  }>
                    {connectivityMutation.data.summaryMessage}
                  </Alert>
                  <List dense disablePadding>
                    {connectivityMutation.data.probes.map((probe) => (
                      <ListItem key={probe.key} disableGutters>
                        <ListItemText
                          primary={(
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {probe.label}
                              </Typography>
                              <Chip label={probe.status} size="small" color={chipColorForStatus(probe.status)} />
                            </Stack>
                          )}
                          secondary={`${probe.endpoint || 'No endpoint'} · ${probe.message}`}
                        />
                      </ListItem>
                    ))}
                  </List>
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  No vendor probes have been run yet for this saved draft.
                </Typography>
              )}
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      {selectedDeploymentId ? (
        <Grid container spacing={2.5}>
          <Grid item xs={12} lg={7}>
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Stack spacing={2.5}>
                  <Box>
                    <Typography variant="h6">Structured provider settings</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Platform-managed deployments now expose the full provider and vector matrix supported by the
                      framework on this branch. Secrets stay in the secret workspace; deployment-specific non-secret
                      settings stay here.
                    </Typography>
                  </Box>

                  {draftQuery.isLoading ? (
                    <Typography color="text.secondary">Loading provider config...</Typography>
                  ) : draftQuery.isError ? (
                    <Alert severity="error">
                      {draftQuery.error instanceof Error
                        ? draftQuery.error.message
                        : 'Failed to load provider config'}
                    </Alert>
                  ) : (
                    <>
                      {providerSetupGuides.length > 0 ? (
                        <Stack spacing={1.25}>
                          {providerSetupGuides.map((guide) => (
                            <Alert key={guide.key} severity={guide.severity}>
                              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                {guide.title}
                              </Typography>
                              <List dense disablePadding sx={{ mt: 0.5 }}>
                                {guide.lines.map((line) => (
                                  <ListItem key={line} disableGutters>
                                    <ListItemText primary={line} />
                                  </ListItem>
                                ))}
                              </List>
                            </Alert>
                          ))}
                        </Stack>
                      ) : null}

                      <Grid container spacing={2}>
                        <Grid item xs={12} md={6}>
                          <TextField
                            select
                            fullWidth
                            label="LLM provider"
                            value={formState.llmProvider}
                            onChange={(event) => handleFieldChange('llmProvider', event.target.value)}
                            helperText="Managed runtime supports OpenAI, Azure OpenAI, Anthropic, Cohere, and Gemini."
                          >
                            <MenuItem value="openai">OpenAI</MenuItem>
                            <MenuItem value="azure">Azure OpenAI</MenuItem>
                            <MenuItem value="anthropic">Anthropic</MenuItem>
                            <MenuItem value="cohere">Cohere</MenuItem>
                            <MenuItem value="gemini">Gemini</MenuItem>
                          </TextField>
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            select
                            fullWidth
                            label="Embedding provider"
                            value={formState.embeddingProvider}
                            onChange={(event) => handleFieldChange('embeddingProvider', event.target.value)}
                            helperText="ONNX runs locally. Remote providers use platform-managed secrets and deployment-scoped connection settings."
                          >
                            <MenuItem value="openai">OpenAI</MenuItem>
                            <MenuItem value="azure">Azure OpenAI</MenuItem>
                            <MenuItem value="cohere">Cohere</MenuItem>
                            <MenuItem value="gemini">Gemini</MenuItem>
                            <MenuItem value="onnx">ONNX</MenuItem>
                            <MenuItem value="rest">REST embedding service</MenuItem>
                          </TextField>
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            select
                            fullWidth
                            label="Vector strategy"
                            value={formState.vectorStrategy}
                            onChange={(event) => handleFieldChange('vectorStrategy', event.target.value)}
                            helperText="Choose local Lucene/Memory or an external managed vector backend."
                          >
                            <MenuItem value="lucene">Lucene</MenuItem>
                            <MenuItem value="memory">Memory</MenuItem>
                            <MenuItem value="qdrant">Qdrant</MenuItem>
                            <MenuItem value="pinecone">Pinecone</MenuItem>
                            <MenuItem value="weaviate">Weaviate</MenuItem>
                            <MenuItem value="milvus">Milvus</MenuItem>
                          </TextField>
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            select
                            fullWidth
                            label="Vector provisioning mode"
                            value={formState.vectorProvisioningMode}
                            onChange={(event) => handleFieldChange('vectorProvisioningMode', event.target.value)}
                            helperText={provisioningModeGuidance(formState.vectorStrategy, formState.vectorProvisioningMode)}
                          >
                            {vectorProvisioningOptions(formState.vectorStrategy).map((option) => (
                              <MenuItem key={option.value} value={option.value}>
                                {option.label}
                              </MenuItem>
                            ))}
                          </TextField>
                        </Grid>
                        <Grid item xs={12}>
                          <Alert severity={provisioningCapabilityMessage(formState.vectorStrategy).severity}>
                            {provisioningCapabilityMessage(formState.vectorStrategy).message}
                          </Alert>
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            select
                            fullWidth
                            label="Runtime profile"
                            value={formState.runtimeProfile}
                            onChange={(event) => handleFieldChange('runtimeProfile', event.target.value)}
                            helperText="Use runtime-managed for secure platform-driven deployments. runtime-dev remains explicit dev-only."
                          >
                            <MenuItem value="runtime-managed">runtime-managed</MenuItem>
                            <MenuItem value="runtime-dev">runtime-dev</MenuItem>
                          </TextField>
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            select
                            fullWidth
                            label="Connector profile"
                            value={formState.connectorProfile}
                            onChange={(event) => handleFieldChange('connectorProfile', event.target.value)}
                            helperText="connector-hosted exposes runtime proxy surfaces from the REST connector."
                          >
                            <MenuItem value="connector-hosted">connector-hosted</MenuItem>
                            <MenuItem value="connector-passive">connector-passive</MenuItem>
                          </TextField>
                        </Grid>
                        <Grid item xs={12}>
                          <FormControlLabel
                            control={(
                              <Checkbox
                                checked={formState.enableFallback}
                                onChange={(event) => handleFieldChange('enableFallback', event.target.checked)}
                              />
                            )}
                            label="Enable provider fallback when the preferred LLM or embedding provider fails"
                          />
                        </Grid>
                        <Grid item xs={12}>
                          <Divider textAlign="left">Purpose-specific LLM routing</Divider>
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <TextField
                            select
                            fullWidth
                            label="Orchestration provider override"
                            value={formState.orchestrationLlmProvider}
                            onChange={(event) => handleFieldChange('orchestrationLlmProvider', event.target.value)}
                            helperText="Optional. Leave blank to use the primary LLM provider."
                          >
                            <MenuItem value="">Use primary provider</MenuItem>
                            <MenuItem value="openai">OpenAI</MenuItem>
                            <MenuItem value="azure">Azure OpenAI</MenuItem>
                            <MenuItem value="anthropic">Anthropic</MenuItem>
                            <MenuItem value="cohere">Cohere</MenuItem>
                            <MenuItem value="gemini">Gemini</MenuItem>
                          </TextField>
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <TextField
                            fullWidth
                            label="Orchestration model override"
                            value={formState.orchestrationModel}
                            onChange={(event) => handleFieldChange('orchestrationModel', event.target.value)}
                            helperText="Optional. Used only for orchestration requests."
                          />
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <TextField
                            fullWidth
                            label="Orchestration temperature"
                            value={formState.orchestrationTemperature}
                            onChange={(event) => handleFieldChange('orchestrationTemperature', event.target.value)}
                            helperText="Optional. 0.0 to 2.0."
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            label="Orchestration max tokens"
                            value={formState.orchestrationMaxTokens}
                            onChange={(event) => handleFieldChange('orchestrationMaxTokens', event.target.value)}
                            helperText="Optional request cap for orchestration."
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            label="Orchestration timeout (s)"
                            value={formState.orchestrationTimeout}
                            onChange={(event) => handleFieldChange('orchestrationTimeout', event.target.value)}
                            helperText="Optional request timeout for orchestration."
                          />
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <TextField
                            select
                            fullWidth
                            label="Generation provider override"
                            value={formState.generationLlmProvider}
                            onChange={(event) => handleFieldChange('generationLlmProvider', event.target.value)}
                            helperText="Optional. Leave blank to use the primary LLM provider."
                          >
                            <MenuItem value="">Use primary provider</MenuItem>
                            <MenuItem value="openai">OpenAI</MenuItem>
                            <MenuItem value="azure">Azure OpenAI</MenuItem>
                            <MenuItem value="anthropic">Anthropic</MenuItem>
                            <MenuItem value="cohere">Cohere</MenuItem>
                            <MenuItem value="gemini">Gemini</MenuItem>
                          </TextField>
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <TextField
                            fullWidth
                            label="Generation model override"
                            value={formState.generationModel}
                            onChange={(event) => handleFieldChange('generationModel', event.target.value)}
                            helperText="Optional. Used for answer generation requests."
                          />
                        </Grid>
                        <Grid item xs={12} md={4}>
                          <TextField
                            fullWidth
                            label="Generation temperature"
                            value={formState.generationTemperature}
                            onChange={(event) => handleFieldChange('generationTemperature', event.target.value)}
                            helperText="Optional. 0.0 to 2.0."
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            label="Generation max tokens"
                            value={formState.generationMaxTokens}
                            onChange={(event) => handleFieldChange('generationMaxTokens', event.target.value)}
                            helperText="Optional request cap for answer generation."
                          />
                        </Grid>
                        <Grid item xs={12} md={6}>
                          <TextField
                            fullWidth
                            label="Generation timeout (s)"
                            value={formState.generationTimeout}
                            onChange={(event) => handleFieldChange('generationTimeout', event.target.value)}
                            helperText="Optional request timeout for generation."
                          />
                        </Grid>

                        {usesOpenAiProvider ? (
                          <>
                            <Grid item xs={12}>
                              <FormControlLabel
                                control={(
                                  <Checkbox
                                    checked={formState.openaiValidateOnStartup}
                                    onChange={(event) => handleFieldChange('openaiValidateOnStartup', event.target.checked)}
                                  />
                                )}
                                label="Validate OpenAI provider on runtime startup"
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <TextField
                                fullWidth
                                label="OpenAI base URL"
                                value={formState.openaiBaseUrl}
                                onChange={(event) => handleFieldChange('openaiBaseUrl', event.target.value)}
                                helperText="Optional. Use for private gateways or OpenAI-compatible routed endpoints."
                              />
                            </Grid>
                            {selectedLlmProviderSet.has('openai') ? (
                              <Grid item xs={12} md={6}>
                                <TextField
                                  fullWidth
                                  label="OpenAI LLM model"
                                  value={formState.openaiModel}
                                  onChange={(event) => handleFieldChange('openaiModel', event.target.value)}
                                  helperText="Optional override. Defaults to gpt-4o-mini."
                                />
                              </Grid>
                            ) : null}
                            {formState.embeddingProvider === 'openai' ? (
                              <>
                                <Grid item xs={12} md={6}>
                                  <TextField
                                    fullWidth
                                    label="OpenAI embedding model"
                                    value={formState.openaiEmbeddingModel}
                                    onChange={(event) => handleFieldChange('openaiEmbeddingModel', event.target.value)}
                                    helperText="Optional override. Defaults to text-embedding-3-small."
                                  />
                                </Grid>
                                <Grid item xs={12} md={6}>
                                  <TextField
                                    fullWidth
                                    label="OpenAI embedding dimensions"
                                    value={formState.openaiEmbeddingDimensions}
                                    onChange={(event) => handleFieldChange('openaiEmbeddingDimensions', event.target.value)}
                                    helperText="Optional override. Useful when reducing text-embedding-3 dimensions."
                                  />
                                </Grid>
                              </>
                            ) : null}
                            {selectedLlmProviderSet.has('openai') ? (
                              <>
                                <Grid item xs={12} md={3}>
                                  <TextField
                                    fullWidth
                                    label="OpenAI max tokens"
                                    value={formState.openaiMaxTokens}
                                    onChange={(event) => handleFieldChange('openaiMaxTokens', event.target.value)}
                                    helperText="Optional default token cap."
                                  />
                                </Grid>
                                <Grid item xs={12} md={3}>
                                  <TextField
                                    fullWidth
                                    label="OpenAI temperature"
                                    value={formState.openaiTemperature}
                                    onChange={(event) => handleFieldChange('openaiTemperature', event.target.value)}
                                    helperText="Optional. 0.0 to 2.0."
                                  />
                                </Grid>
                                <Grid item xs={12} md={3}>
                                  <TextField
                                    fullWidth
                                    label="OpenAI timeout (s)"
                                    value={formState.openaiTimeout}
                                    onChange={(event) => handleFieldChange('openaiTimeout', event.target.value)}
                                    helperText="Optional request timeout."
                                  />
                                </Grid>
                                <Grid item xs={12} md={3}>
                                  <TextField
                                    fullWidth
                                    label="OpenAI priority"
                                    value={formState.openaiPriority}
                                    onChange={(event) => handleFieldChange('openaiPriority', event.target.value)}
                                    helperText="Optional provider priority."
                                  />
                                </Grid>
                              </>
                            ) : null}
                          </>
                        ) : null}

                        {usesAnthropicProvider ? (
                          <>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="Anthropic model"
                                value={formState.anthropicModel}
                                onChange={(event) => handleFieldChange('anthropicModel', event.target.value)}
                                helperText="Optional override. Defaults to claude-3-haiku-20240307."
                              />
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="Anthropic base URL"
                                value={formState.anthropicBaseUrl}
                                onChange={(event) => handleFieldChange('anthropicBaseUrl', event.target.value)}
                                helperText="Optional. Use for private routing or regional gateways."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Anthropic max tokens"
                                value={formState.anthropicMaxTokens}
                                onChange={(event) => handleFieldChange('anthropicMaxTokens', event.target.value)}
                                helperText="Optional default token cap."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Anthropic temperature"
                                value={formState.anthropicTemperature}
                                onChange={(event) => handleFieldChange('anthropicTemperature', event.target.value)}
                                helperText="Optional. 0.0 to 2.0."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Anthropic timeout (s)"
                                value={formState.anthropicTimeout}
                                onChange={(event) => handleFieldChange('anthropicTimeout', event.target.value)}
                                helperText="Optional request timeout."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Anthropic priority"
                                value={formState.anthropicPriority}
                                onChange={(event) => handleFieldChange('anthropicPriority', event.target.value)}
                                helperText="Optional provider priority."
                              />
                            </Grid>
                          </>
                        ) : null}

                        {usesAzureProvider ? (
                          <>
                            <Grid item xs={12}>
                              <FormControlLabel
                                control={(
                                  <Checkbox
                                    checked={formState.azureValidateOnStartup}
                                    onChange={(event) => handleFieldChange('azureValidateOnStartup', event.target.checked)}
                                  />
                                )}
                                label="Validate Azure provider on runtime startup"
                              />
                            </Grid>
                            <Grid item xs={12} md={8}>
                              <TextField
                                fullWidth
                                label="Azure endpoint"
                                value={formState.azureEndpoint}
                                onChange={(event) => handleFieldChange('azureEndpoint', event.target.value)}
                                helperText="Required. Example: https://your-resource.openai.azure.com"
                              />
                            </Grid>
                            <Grid item xs={12} md={4}>
                              <TextField
                                fullWidth
                                label="Azure API version"
                                value={formState.azureApiVersion}
                                onChange={(event) => handleFieldChange('azureApiVersion', event.target.value)}
                                helperText="Default 2024-02-15-preview"
                              />
                            </Grid>
                            {selectedLlmProviderSet.has('azure') ? (
                              <Grid item xs={12} md={6}>
                                <TextField
                                  fullWidth
                                  label="Azure LLM deployment"
                                  value={formState.azureDeploymentName}
                                  onChange={(event) => handleFieldChange('azureDeploymentName', event.target.value)}
                                  helperText="Required. Customer-defined Azure deployment name."
                                />
                              </Grid>
                            ) : null}
                            {formState.embeddingProvider === 'azure' ? (
                              <Grid item xs={12} md={6}>
                                <TextField
                                  fullWidth
                                  label="Azure embedding deployment"
                                  value={formState.azureEmbeddingDeploymentName}
                                  onChange={(event) => handleFieldChange('azureEmbeddingDeploymentName', event.target.value)}
                                  helperText="Required. Customer-defined Azure embedding deployment."
                                />
                              </Grid>
                            ) : null}
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="Azure timeout (s)"
                                value={formState.azureTimeout}
                                onChange={(event) => handleFieldChange('azureTimeout', event.target.value)}
                                helperText="Optional request timeout."
                              />
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="Azure priority"
                                value={formState.azurePriority}
                                onChange={(event) => handleFieldChange('azurePriority', event.target.value)}
                                helperText="Optional provider priority."
                              />
                            </Grid>
                          </>
                        ) : null}

                        {usesCohereProvider ? (
                          <>
                            <Grid item xs={12}>
                              <FormControlLabel
                                control={(
                                  <Checkbox
                                    checked={formState.cohereValidateOnStartup}
                                    onChange={(event) => handleFieldChange('cohereValidateOnStartup', event.target.checked)}
                                  />
                                )}
                                label="Validate Cohere provider on runtime startup"
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <TextField
                                fullWidth
                                label="Cohere base URL"
                                value={formState.cohereBaseUrl}
                                onChange={(event) => handleFieldChange('cohereBaseUrl', event.target.value)}
                                helperText="Optional. Override when traffic must go through a private or regional gateway."
                              />
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="Cohere LLM model"
                                value={formState.cohereModel}
                                onChange={(event) => handleFieldChange('cohereModel', event.target.value)}
                                helperText="Optional override. Defaults to command-r7b-12-2024."
                              />
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="Cohere embedding model"
                                value={formState.cohereEmbeddingModel}
                                onChange={(event) => handleFieldChange('cohereEmbeddingModel', event.target.value)}
                                helperText="Optional override. Defaults to embed-english-v3.0."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Cohere max tokens"
                                value={formState.cohereMaxTokens}
                                onChange={(event) => handleFieldChange('cohereMaxTokens', event.target.value)}
                                helperText="Optional default token cap."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Cohere temperature"
                                value={formState.cohereTemperature}
                                onChange={(event) => handleFieldChange('cohereTemperature', event.target.value)}
                                helperText="Optional. 0.0 to 2.0."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Cohere timeout (s)"
                                value={formState.cohereTimeout}
                                onChange={(event) => handleFieldChange('cohereTimeout', event.target.value)}
                                helperText="Optional request timeout."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Cohere priority"
                                value={formState.coherePriority}
                                onChange={(event) => handleFieldChange('coherePriority', event.target.value)}
                                helperText="Optional provider priority."
                              />
                            </Grid>
                          </>
                        ) : null}

                        {usesGeminiProvider ? (
                          <>
                            <Grid item xs={12}>
                              <FormControlLabel
                                control={(
                                  <Checkbox
                                    checked={formState.geminiValidateOnStartup}
                                    onChange={(event) => handleFieldChange('geminiValidateOnStartup', event.target.checked)}
                                  />
                                )}
                                label="Validate Gemini provider on runtime startup"
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <TextField
                                fullWidth
                                label="Gemini base URL"
                                value={formState.geminiBaseUrl}
                                onChange={(event) => handleFieldChange('geminiBaseUrl', event.target.value)}
                                helperText="Optional. Override when Gemini traffic must use a private or regional gateway."
                              />
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="Gemini LLM model"
                                value={formState.geminiModel}
                                onChange={(event) => handleFieldChange('geminiModel', event.target.value)}
                                helperText="Optional override. Defaults to gemini-1.5-flash."
                              />
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="Gemini embedding model"
                                value={formState.geminiEmbeddingModel}
                                onChange={(event) => handleFieldChange('geminiEmbeddingModel', event.target.value)}
                                helperText="Optional override. Defaults to text-embedding-004."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Gemini max tokens"
                                value={formState.geminiMaxTokens}
                                onChange={(event) => handleFieldChange('geminiMaxTokens', event.target.value)}
                                helperText="Optional default token cap."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Gemini temperature"
                                value={formState.geminiTemperature}
                                onChange={(event) => handleFieldChange('geminiTemperature', event.target.value)}
                                helperText="Optional. 0.0 to 2.0."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Gemini timeout (s)"
                                value={formState.geminiTimeout}
                                onChange={(event) => handleFieldChange('geminiTimeout', event.target.value)}
                                helperText="Optional request timeout."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Gemini priority"
                                value={formState.geminiPriority}
                                onChange={(event) => handleFieldChange('geminiPriority', event.target.value)}
                                helperText="Optional provider priority."
                              />
                            </Grid>
                          </>
                        ) : null}

                        {formState.embeddingProvider === 'onnx' ? (
                          <>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="ONNX model alias"
                                value={formState.onnxModelAlias}
                                onChange={(event) => handleFieldChange('onnxModelAlias', event.target.value)}
                                helperText="Optional override. Defaults to all-MiniLM-L6-v2."
                              />
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="ONNX max sequence length"
                                value={formState.onnxMaxSequenceLength}
                                onChange={(event) => handleFieldChange('onnxMaxSequenceLength', event.target.value)}
                                helperText="Default 512"
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <TextField
                                fullWidth
                                label="ONNX model path"
                                value={formState.onnxModelPath}
                                onChange={(event) => handleFieldChange('onnxModelPath', event.target.value)}
                                helperText="Optional. Override the bundled model path inside the runtime container."
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <TextField
                                fullWidth
                                label="ONNX tokenizer path"
                                value={formState.onnxTokenizerPath}
                                onChange={(event) => handleFieldChange('onnxTokenizerPath', event.target.value)}
                                helperText="Optional. Override the bundled tokenizer path inside the runtime container."
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <FormControlLabel
                                control={(
                                  <Checkbox
                                    checked={formState.onnxUseGpu}
                                    onChange={(event) => handleFieldChange('onnxUseGpu', event.target.checked)}
                                  />
                                )}
                                label="Enable ONNX GPU acceleration"
                              />
                            </Grid>
                          </>
                        ) : null}

                        {formState.embeddingProvider === 'rest' ? (
                          <>
                            <Grid item xs={12}>
                              <FormControlLabel
                                control={(
                                  <Checkbox
                                    checked={formState.restEmbeddingValidateOnStartup}
                                    onChange={(event) => handleFieldChange('restEmbeddingValidateOnStartup', event.target.checked)}
                                  />
                                )}
                                label="Validate REST embedding service on runtime startup"
                              />
                            </Grid>
                            <Grid item xs={12} md={8}>
                              <TextField
                                fullWidth
                                label="REST embedding base URL"
                                value={formState.restEmbeddingBaseUrl}
                                onChange={(event) => handleFieldChange('restEmbeddingBaseUrl', event.target.value)}
                                helperText="Required. Base URL of the external embedding service."
                              />
                            </Grid>
                            <Grid item xs={12} md={4}>
                              <TextField
                                fullWidth
                                label="REST timeout (ms)"
                                value={formState.restEmbeddingTimeoutMs}
                                onChange={(event) => handleFieldChange('restEmbeddingTimeoutMs', event.target.value)}
                                helperText="Default 30000"
                              />
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="REST endpoint"
                                value={formState.restEmbeddingEndpoint}
                                onChange={(event) => handleFieldChange('restEmbeddingEndpoint', event.target.value)}
                                helperText="Default /embed"
                              />
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="REST batch endpoint"
                                value={formState.restEmbeddingBatchEndpoint}
                                onChange={(event) => handleFieldChange('restEmbeddingBatchEndpoint', event.target.value)}
                                helperText="Default /embed/batch"
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <TextField
                                fullWidth
                                label="REST embedding model"
                                value={formState.restEmbeddingModel}
                                onChange={(event) => handleFieldChange('restEmbeddingModel', event.target.value)}
                                helperText="Optional model identifier forwarded to the embedding service."
                              />
                            </Grid>
                          </>
                        ) : null}

                        {formState.vectorStrategy === 'qdrant' ? (
                          <>
                            {formState.vectorProvisioningMode === 'PLATFORM_MANAGED' ? (
                              <>
                                <Grid item xs={12}>
                                  <Alert severity="info">
                                    Platform-managed Qdrant uses the formal Qdrant Cloud control plane. The platform will create or reuse a managed cluster, issue a deployment-scoped database key, and wire the resolved endpoint back into runtime config.
                                  </Alert>
                                </Grid>
                                <Grid item xs={12} md={4}>
                                  <TextField
                                    select
                                    fullWidth
                                    label="Qdrant Cloud provider"
                                    value={formState.qdrantCloudProviderId}
                                    onChange={(event) => handleFieldChange('qdrantCloudProviderId', event.target.value)}
                                    helperText="Default aws"
                                  >
                                    <MenuItem value="aws">AWS</MenuItem>
                                    <MenuItem value="gcp">GCP</MenuItem>
                                    <MenuItem value="azure">Azure</MenuItem>
                                  </TextField>
                                </Grid>
                                <Grid item xs={12} md={4}>
                                  <TextField
                                    fullWidth
                                    label="Qdrant Cloud region"
                                    value={formState.qdrantCloudRegionId}
                                    onChange={(event) => handleFieldChange('qdrantCloudRegionId', event.target.value)}
                                    helperText="Required. Example: eu-west-1"
                                  />
                                </Grid>
                                <Grid item xs={12} md={4}>
                                  <TextField
                                    fullWidth
                                    label="Qdrant Cloud account ID"
                                    value={formState.qdrantCloudAccountId}
                                    onChange={(event) => handleFieldChange('qdrantCloudAccountId', event.target.value)}
                                    helperText="Optional. Leave blank to auto-resolve when the management key only exposes one account."
                                  />
                                </Grid>
                                <Grid item xs={12} md={6}>
                                  <TextField
                                    fullWidth
                                    label="Qdrant Cloud package ID"
                                    value={formState.qdrantCloudPackageId}
                                    onChange={(event) => handleFieldChange('qdrantCloudPackageId', event.target.value)}
                                    helperText="Optional. Leave blank to let the platform choose the cheapest active package."
                                  />
                                </Grid>
                                <Grid item xs={12} md={6}>
                                  <TextField
                                    fullWidth
                                    label="Qdrant cluster name override"
                                    value={formState.qdrantCloudClusterNameOverride}
                                    onChange={(event) => handleFieldChange('qdrantCloudClusterNameOverride', event.target.value)}
                                    helperText="Optional. Otherwise the cluster name is derived from the deployment id."
                                  />
                                </Grid>
                              </>
                            ) : (
                              <>
                                <Grid item xs={12} md={6}>
                                  <TextField
                                    fullWidth
                                    label="Qdrant host"
                                    value={formState.qdrantHost}
                                    onChange={(event) => handleFieldChange('qdrantHost', event.target.value)}
                                    helperText="Required. Hostname, internal address, or full https:// cluster endpoint for the target Qdrant deployment."
                                  />
                                </Grid>
                                <Grid item xs={12} md={2}>
                                  <TextField
                                    fullWidth
                                    label="Qdrant port"
                                    value={formState.qdrantPort}
                                    onChange={(event) => handleFieldChange('qdrantPort', event.target.value)}
                                    helperText="Default 6333"
                                  />
                                </Grid>
                                <Grid item xs={12} md={2}>
                                  <TextField
                                    fullWidth
                                    label="Qdrant gRPC port"
                                    value={formState.qdrantGrpcPort}
                                    onChange={(event) => handleFieldChange('qdrantGrpcPort', event.target.value)}
                                    helperText="Default 6334"
                                  />
                                </Grid>
                              </>
                            )}
                            <Grid item xs={12} md={2}>
                              <TextField
                                fullWidth
                                label="Qdrant timeout (s)"
                                value={formState.qdrantTimeout}
                                onChange={(event) => handleFieldChange('qdrantTimeout', event.target.value)}
                                helperText="Optional request timeout."
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <FormControlLabel
                                control={(
                                  <Checkbox
                                    checked={formState.qdrantPreferGrpc}
                                    onChange={(event) => handleFieldChange('qdrantPreferGrpc', event.target.checked)}
                                  />
                                )}
                                label="Prefer Qdrant gRPC transport"
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <FormControlLabel
                                control={(
                                  <Checkbox
                                    checked={formState.qdrantManagedCollectionsEnabled}
                                    onChange={(event) => handleFieldChange('qdrantManagedCollectionsEnabled', event.target.checked)}
                                    disabled={formState.vectorProvisioningMode === 'PLATFORM_MANAGED'}
                                  />
                                )}
                                label={formState.vectorProvisioningMode === 'PLATFORM_MANAGED'
                                  ? 'Platform-managed Qdrant Cloud always reconciles one collection per configured entity type'
                                  : 'Let the platform create or reconcile Qdrant collections for this deployment'}
                              />
                            </Grid>
                          </>
                        ) : null}

                        {formState.vectorStrategy === 'pinecone' ? (
                          <>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="Pinecone index"
                                value={formState.pineconeIndexName}
                                onChange={(event) => handleFieldChange('pineconeIndexName', event.target.value)}
                                helperText={formState.vectorProvisioningMode === 'PLATFORM_MANAGED'
                                  ? 'Required. The platform manages this serverless index name directly.'
                                  : 'Required unless the platform can derive it from the API host.'}
                              />
                            </Grid>
                            <Grid item xs={12} md={4}>
                              <TextField
                                fullWidth
                                label="Pinecone dimensions"
                                value={formState.pineconeDimensions}
                                onChange={(event) => handleFieldChange('pineconeDimensions', event.target.value)}
                                helperText="Default 1536"
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <Alert severity={formState.vectorProvisioningMode === 'PLATFORM_MANAGED' ? 'info' : 'warning'}>
                                {formState.vectorProvisioningMode === 'PLATFORM_MANAGED'
                                  ? 'Pinecone platform-managed mode is active. Apply will create or reconcile the serverless index through the provider control plane, resolve the host, and bind runtime to that managed resource.'
                                  : 'Pinecone is currently in bring-your-own mode. The target index and API host binding must already exist before runtime traffic depends on it.'}
                              </Alert>
                            </Grid>
                            {formState.vectorProvisioningMode === 'PLATFORM_MANAGED' ? (
                              <>
                                <Grid item xs={12} md={4}>
                                  <TextField
                                    fullWidth
                                    label="Pinecone cloud"
                                    value={formState.pineconeCloud}
                                    onChange={(event) => handleFieldChange('pineconeCloud', event.target.value)}
                                    helperText="Used for platform-managed serverless index provisioning. Example: aws"
                                  />
                                </Grid>
                                <Grid item xs={12} md={4}>
                                  <TextField
                                    fullWidth
                                    label="Pinecone region"
                                    value={formState.pineconeRegion}
                                    onChange={(event) => handleFieldChange('pineconeRegion', event.target.value)}
                                    helperText="Required for platform-managed serverless index provisioning. Example: us-east-1"
                                  />
                                </Grid>
                                <Grid item xs={12} md={4}>
                                  <TextField
                                    fullWidth
                                    label="Pinecone metric"
                                    value={formState.pineconeMetric}
                                    onChange={(event) => handleFieldChange('pineconeMetric', event.target.value)}
                                    helperText="Supported values: cosine, dotproduct, euclidean"
                                  />
                                </Grid>
                                <Grid item xs={12}>
                                  <TextField
                                    fullWidth
                                    label="Resolved Pinecone API host"
                                    value={formState.pineconeApiHost}
                                    InputProps={{ readOnly: true }}
                                    helperText="Resolved during apply after Pinecone serverless provisioning completes."
                                  />
                                </Grid>
                                <Grid item xs={12}>
                                  <FormControlLabel
                                    control={(
                                      <Checkbox
                                        checked={formState.pineconeDeletionProtectionEnabled}
                                        onChange={(event) => handleFieldChange('pineconeDeletionProtectionEnabled', event.target.checked)}
                                      />
                                    )}
                                    label="Enable Pinecone deletion protection for the platform-managed index"
                                  />
                                </Grid>
                              </>
                            ) : (
                              <>
                                <Grid item xs={12} md={6}>
                                  <TextField
                                    fullWidth
                                    label="Pinecone environment"
                                    value={formState.pineconeEnvironment}
                                    onChange={(event) => handleFieldChange('pineconeEnvironment', event.target.value)}
                                    helperText="Required unless API host is supplied."
                                  />
                                </Grid>
                                <Grid item xs={12} md={6}>
                                  <TextField
                                    fullWidth
                                    label="Pinecone project ID"
                                    value={formState.pineconeProjectId}
                                    onChange={(event) => handleFieldChange('pineconeProjectId', event.target.value)}
                                    helperText="Optional. Used when the host should be composed from index, project, and environment."
                                  />
                                </Grid>
                                <Grid item xs={12}>
                                  <TextField
                                    fullWidth
                                    label="Pinecone API host"
                                    value={formState.pineconeApiHost}
                                    onChange={(event) => handleFieldChange('pineconeApiHost', event.target.value)}
                                    helperText="Optional. Use when you want to target a fully qualified Pinecone host."
                                  />
                                </Grid>
                              </>
                            )}
                          </>
                        ) : null}

                        {formState.vectorStrategy === 'weaviate' ? (
                          <>
                            <Grid item xs={12}>
                              <Alert severity="info">
                                Weaviate uses external managed-service mode in the platform. Point the deployment at an existing Weaviate Cloud cluster, then let the platform verify connectivity and bind runtime config.
                              </Alert>
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Weaviate scheme"
                                value={formState.weaviateScheme}
                                onChange={(event) => handleFieldChange('weaviateScheme', event.target.value)}
                                helperText="Default https"
                              />
                            </Grid>
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="Weaviate host"
                                value={formState.weaviateHost}
                                onChange={(event) => handleFieldChange('weaviateHost', event.target.value)}
                                helperText="Required. Hostname of the Weaviate cluster."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Weaviate port"
                                value={formState.weaviatePort}
                                onChange={(event) => handleFieldChange('weaviatePort', event.target.value)}
                                helperText="Default 443"
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Weaviate timeout (s)"
                                value={formState.weaviateTimeout}
                                onChange={(event) => handleFieldChange('weaviateTimeout', event.target.value)}
                                helperText="Optional request timeout."
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <FormControlLabel
                                control={(
                                  <Checkbox
                                    checked={formState.weaviateConsistencyLevelStrong}
                                    onChange={(event) => handleFieldChange('weaviateConsistencyLevelStrong', event.target.checked)}
                                  />
                                )}
                                label="Request strong consistency from Weaviate"
                              />
                            </Grid>
                          </>
                        ) : null}

                        {formState.vectorStrategy === 'milvus' ? (
                          <>
                            <Grid item xs={12}>
                              <Alert severity={formState.vectorProvisioningMode === 'PLATFORM_MANAGED' ? 'info' : 'warning'}>
                                {formState.vectorProvisioningMode === 'PLATFORM_MANAGED'
                                  ? 'Zilliz Cloud platform-managed mode is active. Apply will provision or reuse a managed Zilliz Cloud cluster, store deployment-scoped runtime credentials, and bind the resolved Milvus endpoint into runtime config.'
                                  : 'Milvus is in bring-your-own mode. Use this for an existing Milvus or Zilliz endpoint that you operate outside the platform.'}
                              </Alert>
                            </Grid>
                            {formState.vectorProvisioningMode === 'PLATFORM_MANAGED' ? (
                              <>
                                <Grid item xs={12} md={4}>
                                  <TextField
                                    fullWidth
                                    label="Zilliz Cloud project ID"
                                    value={formState.zillizCloudProjectId}
                                    onChange={(event) => handleFieldChange('zillizCloudProjectId', event.target.value)}
                                    helperText="Required. The project that owns the managed cluster."
                                  />
                                </Grid>
                                <Grid item xs={12} md={4}>
                                  <TextField
                                    fullWidth
                                    label="Zilliz Cloud region ID"
                                    value={formState.zillizCloudRegionId}
                                    onChange={(event) => handleFieldChange('zillizCloudRegionId', event.target.value)}
                                    helperText="Required. Example: gcp-us-west1 or aws-eu-central-1."
                                  />
                                </Grid>
                                <Grid item xs={12} md={4}>
                                  <TextField
                                    select
                                    fullWidth
                                    label="Zilliz cluster plan"
                                    value={formState.zillizCloudClusterPlan}
                                    onChange={(event) => handleFieldChange('zillizCloudClusterPlan', event.target.value)}
                                    helperText="Choose the managed Zilliz Cloud plan the platform should provision."
                                  >
                                    <MenuItem value="Free">Free</MenuItem>
                                    <MenuItem value="Serverless">Serverless</MenuItem>
                                    <MenuItem value="Standard">Standard</MenuItem>
                                    <MenuItem value="Enterprise">Enterprise</MenuItem>
                                  </TextField>
                                </Grid>
                                <Grid item xs={12} md={6}>
                                  <TextField
                                    fullWidth
                                    label="Zilliz cluster name override"
                                    value={formState.zillizCloudClusterNameOverride}
                                    onChange={(event) => handleFieldChange('zillizCloudClusterNameOverride', event.target.value)}
                                    helperText="Optional. Otherwise the cluster name is derived from the deployment id."
                                  />
                                </Grid>
                                <Grid item xs={12} md={6}>
                                  <TextField
                                    fullWidth
                                    label="Resolved Milvus host"
                                    value={formState.milvusHost}
                                    InputProps={{ readOnly: true }}
                                    helperText="Resolved during apply after Zilliz Cloud provisioning completes."
                                  />
                                </Grid>
                                {['Standard', 'Enterprise'].includes(formState.zillizCloudClusterPlan.trim()) ? (
                                  <>
                                    <Grid item xs={12} md={3}>
                                      <TextField
                                        select
                                        fullWidth
                                        label="Zilliz CU type"
                                        value={formState.zillizCloudCuType}
                                        onChange={(event) => handleFieldChange('zillizCloudCuType', event.target.value)}
                                        helperText="Required for Standard and Enterprise clusters."
                                      >
                                        <MenuItem value="Performance-optimized">Performance-optimized</MenuItem>
                                        <MenuItem value="Capacity-optimized">Capacity-optimized</MenuItem>
                                      </TextField>
                                    </Grid>
                                    <Grid item xs={12} md={3}>
                                      <TextField
                                        fullWidth
                                        label="Zilliz CU size"
                                        value={formState.zillizCloudCuSize}
                                        onChange={(event) => handleFieldChange('zillizCloudCuSize', event.target.value)}
                                        helperText="Required positive integer for Standard and Enterprise clusters."
                                      />
                                    </Grid>
                                  </>
                                ) : null}
                                <Grid item xs={12} md={3}>
                                  <TextField
                                    fullWidth
                                    label="Resolved Milvus port"
                                    value={formState.milvusPort}
                                    InputProps={{ readOnly: true }}
                                    helperText="Resolved during apply after Zilliz Cloud provisioning completes."
                                  />
                                </Grid>
                              </>
                            ) : (
                              <>
                                <Grid item xs={12} md={6}>
                                  <TextField
                                    fullWidth
                                    label="Milvus host"
                                    value={formState.milvusHost}
                                    onChange={(event) => handleFieldChange('milvusHost', event.target.value)}
                                    helperText="Required. Hostname of the Milvus cluster."
                                  />
                                </Grid>
                                <Grid item xs={12} md={2}>
                                  <TextField
                                    fullWidth
                                    label="Milvus port"
                                    value={formState.milvusPort}
                                    onChange={(event) => handleFieldChange('milvusPort', event.target.value)}
                                    helperText="Default 19530"
                                  />
                                </Grid>
                              </>
                            )}
                            <Grid item xs={12} md={2}>
                              <TextField
                                fullWidth
                                label="Milvus database"
                                value={formState.milvusDatabaseName}
                                onChange={(event) => handleFieldChange('milvusDatabaseName', event.target.value)}
                                helperText="Default default"
                              />
                            </Grid>
                            <Grid item xs={12} md={2}>
                              <TextField
                                fullWidth
                                label="Milvus timeout (s)"
                                value={formState.milvusTimeout}
                                onChange={(event) => handleFieldChange('milvusTimeout', event.target.value)}
                                helperText="Optional request timeout."
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <FormControlLabel
                                control={(
                                  <Checkbox
                                    checked={formState.milvusSecure}
                                    onChange={(event) => handleFieldChange('milvusSecure', event.target.checked)}
                                  />
                                )}
                                label="Use secure Milvus transport"
                              />
                            </Grid>
                            <Grid item xs={12}>
                              <FormControlLabel
                                control={(
                                  <Checkbox
                                    checked={formState.milvusFlushOnWrite}
                                    onChange={(event) => handleFieldChange('milvusFlushOnWrite', event.target.checked)}
                                  />
                                )}
                                label="Flush Milvus on every write"
                              />
                            </Grid>
                          </>
                        ) : null}
                      </Grid>

                      {saveMutation.isError ? (
                        <Alert severity="error">
                          {saveMutation.error instanceof Error
                            ? saveMutation.error.message
                            : 'Failed to save provider config'}
                        </Alert>
                      ) : null}
                      {saveMutation.isSuccess ? (
                        <Alert severity="success">Provider config draft saved.</Alert>
                      ) : null}

                      <Stack direction="row" spacing={1.5}>
                        <Button
                          variant="contained"
                          startIcon={<SaveRoundedIcon />}
                          onClick={handleSave}
                          disabled={!canEdit || saveMutation.isPending || draftQuery.isLoading || !draftDirty}
                        >
                          {saveMutation.isPending ? 'Saving...' : 'Save provider config'}
                        </Button>
                        <Button
                          variant="outlined"
                          onClick={() => {
                            if (draftQuery.data) {
                              setFormState(readProviderForm(draftQuery.data.providerConfig))
                            }
                          }}
                        >
                          Reset form
                        </Button>
                      </Stack>
                    </>
                  )}
                </Stack>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} lg={5}>
            <Card sx={{ border: '1px solid', borderColor: 'divider', boxShadow: 'none' }}>
              <CardContent>
                <Stack spacing={2}>
                  <Box>
                    <Typography variant="h6">Provider summary</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      High-level summary of the provider profile currently in the form.
                    </Typography>
                  </Box>

                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Chip label={`${summaryItems.length} active settings`} color="primary" />
                    <Chip label={`${formState.llmProvider}/${formState.embeddingProvider}/${formState.vectorStrategy}`} variant="outlined" />
                  </Stack>

                  <Divider />

                  <List dense disablePadding>
                    {summaryItems.map((item) => (
                      <ListItem disableGutters key={item.label}>
                        <ListItemText primary={item.label} secondary={item.value} />
                      </ListItem>
                    ))}
                  </List>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      ) : null}
    </Stack>
  )
}
