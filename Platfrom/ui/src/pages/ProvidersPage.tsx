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
  fetchDeploymentDraft,
  updateDeploymentDraft,
} from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useDeploymentWorkspaceEditorState } from '../workspace/useDeploymentWorkspaceEditorState'

type ProviderFormState = {
  llmProvider: string
  embeddingProvider: string
  vectorStrategy: string
  runtimeProfile: string
  connectorProfile: string
  openaiBaseUrl: string
  openaiModel: string
  openaiEmbeddingModel: string
  openaiEmbeddingDimensions: string
  anthropicBaseUrl: string
  anthropicModel: string
  azureEndpoint: string
  azureDeploymentName: string
  azureEmbeddingDeploymentName: string
  azureApiVersion: string
  cohereBaseUrl: string
  cohereModel: string
  cohereEmbeddingModel: string
  geminiBaseUrl: string
  geminiModel: string
  geminiEmbeddingModel: string
  onnxModelAlias: string
  onnxModelPath: string
  onnxTokenizerPath: string
  onnxMaxSequenceLength: string
  onnxUseGpu: boolean
  qdrantHost: string
  qdrantPort: string
  qdrantGrpcPort: string
  qdrantPreferGrpc: boolean
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
  weaviateScheme: string
  weaviateHost: string
  weaviatePort: string
  weaviateConsistencyLevelStrong: boolean
  milvusHost: string
  milvusPort: string
  milvusDatabaseName: string
  milvusSecure: boolean
  milvusFlushOnWrite: boolean
}

type SummaryItem = {
  label: string
  value: string
}

const DEFAULT_PROVIDER_FORM_STATE: ProviderFormState = {
  llmProvider: 'openai',
  embeddingProvider: 'openai',
  vectorStrategy: 'lucene',
  runtimeProfile: 'runtime-managed',
  connectorProfile: 'connector-hosted',
  openaiBaseUrl: '',
  openaiModel: 'gpt-4o-mini',
  openaiEmbeddingModel: 'text-embedding-3-small',
  openaiEmbeddingDimensions: '1536',
  anthropicBaseUrl: '',
  anthropicModel: 'claude-3-haiku-20240307',
  azureEndpoint: '',
  azureDeploymentName: '',
  azureEmbeddingDeploymentName: '',
  azureApiVersion: '2024-02-15-preview',
  cohereBaseUrl: '',
  cohereModel: 'command-r7b-12-2024',
  cohereEmbeddingModel: 'embed-english-v3.0',
  geminiBaseUrl: '',
  geminiModel: 'gemini-1.5-flash',
  geminiEmbeddingModel: 'text-embedding-004',
  onnxModelAlias: 'all-MiniLM-L6-v2',
  onnxModelPath: '',
  onnxTokenizerPath: '',
  onnxMaxSequenceLength: '512',
  onnxUseGpu: false,
  qdrantHost: '',
  qdrantPort: '6333',
  qdrantGrpcPort: '6334',
  qdrantPreferGrpc: false,
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
  weaviateScheme: 'https',
  weaviateHost: '',
  weaviatePort: '443',
  weaviateConsistencyLevelStrong: false,
  milvusHost: '',
  milvusPort: '19530',
  milvusDatabaseName: 'default',
  milvusSecure: false,
  milvusFlushOnWrite: false,
}

const providerFormKeys: Array<keyof ProviderFormState> = [
  'llmProvider',
  'embeddingProvider',
  'vectorStrategy',
  'runtimeProfile',
  'connectorProfile',
  'openaiBaseUrl',
  'openaiModel',
  'openaiEmbeddingModel',
  'openaiEmbeddingDimensions',
  'anthropicBaseUrl',
  'anthropicModel',
  'azureEndpoint',
  'azureDeploymentName',
  'azureEmbeddingDeploymentName',
  'azureApiVersion',
  'cohereBaseUrl',
  'cohereModel',
  'cohereEmbeddingModel',
  'geminiBaseUrl',
  'geminiModel',
  'geminiEmbeddingModel',
  'onnxModelAlias',
  'onnxModelPath',
  'onnxTokenizerPath',
  'onnxMaxSequenceLength',
  'onnxUseGpu',
  'qdrantHost',
  'qdrantPort',
  'qdrantGrpcPort',
  'qdrantPreferGrpc',
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
  'weaviateScheme',
  'weaviateHost',
  'weaviatePort',
  'weaviateConsistencyLevelStrong',
  'milvusHost',
  'milvusPort',
  'milvusDatabaseName',
  'milvusSecure',
  'milvusFlushOnWrite',
]

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function cloneJson<T>(value: T): T {
  return JSON.parse(JSON.stringify(value ?? null)) as T
}

function readString(config: Record<string, unknown>, key: string, fallback = ''): string {
  const value = config[key]
  return typeof value === 'string' ? value : fallback
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

function readProviderForm(config: unknown): ProviderFormState {
  const record = isRecord(config) ? config : {}
  return {
    llmProvider: readString(record, 'llmProvider', DEFAULT_PROVIDER_FORM_STATE.llmProvider),
    embeddingProvider: readString(record, 'embeddingProvider', DEFAULT_PROVIDER_FORM_STATE.embeddingProvider),
    vectorStrategy: readString(record, 'vectorStrategy', DEFAULT_PROVIDER_FORM_STATE.vectorStrategy),
    runtimeProfile: readString(record, 'runtimeProfile', DEFAULT_PROVIDER_FORM_STATE.runtimeProfile),
    connectorProfile: readString(record, 'connectorProfile', DEFAULT_PROVIDER_FORM_STATE.connectorProfile),
    openaiBaseUrl: readString(record, 'openaiBaseUrl'),
    openaiModel: readString(record, 'openaiModel', DEFAULT_PROVIDER_FORM_STATE.openaiModel),
    openaiEmbeddingModel: readString(record, 'openaiEmbeddingModel', DEFAULT_PROVIDER_FORM_STATE.openaiEmbeddingModel),
    openaiEmbeddingDimensions: readString(record, 'openaiEmbeddingDimensions', DEFAULT_PROVIDER_FORM_STATE.openaiEmbeddingDimensions),
    anthropicBaseUrl: readString(record, 'anthropicBaseUrl'),
    anthropicModel: readString(record, 'anthropicModel', DEFAULT_PROVIDER_FORM_STATE.anthropicModel),
    azureEndpoint: readString(record, 'azureEndpoint'),
    azureDeploymentName: readString(record, 'azureDeploymentName'),
    azureEmbeddingDeploymentName: readString(record, 'azureEmbeddingDeploymentName'),
    azureApiVersion: readString(record, 'azureApiVersion', DEFAULT_PROVIDER_FORM_STATE.azureApiVersion),
    cohereBaseUrl: readString(record, 'cohereBaseUrl'),
    cohereModel: readString(record, 'cohereModel', DEFAULT_PROVIDER_FORM_STATE.cohereModel),
    cohereEmbeddingModel: readString(record, 'cohereEmbeddingModel', DEFAULT_PROVIDER_FORM_STATE.cohereEmbeddingModel),
    geminiBaseUrl: readString(record, 'geminiBaseUrl'),
    geminiModel: readString(record, 'geminiModel', DEFAULT_PROVIDER_FORM_STATE.geminiModel),
    geminiEmbeddingModel: readString(record, 'geminiEmbeddingModel', DEFAULT_PROVIDER_FORM_STATE.geminiEmbeddingModel),
    onnxModelAlias: readString(record, 'onnxModelAlias', DEFAULT_PROVIDER_FORM_STATE.onnxModelAlias),
    onnxModelPath: readString(record, 'onnxModelPath'),
    onnxTokenizerPath: readString(record, 'onnxTokenizerPath'),
    onnxMaxSequenceLength: readString(record, 'onnxMaxSequenceLength', DEFAULT_PROVIDER_FORM_STATE.onnxMaxSequenceLength),
    onnxUseGpu: readBoolean(record, 'onnxUseGpu'),
    qdrantHost: readString(record, 'qdrantHost'),
    qdrantPort: readString(record, 'qdrantPort', DEFAULT_PROVIDER_FORM_STATE.qdrantPort),
    qdrantGrpcPort: readString(record, 'qdrantGrpcPort', DEFAULT_PROVIDER_FORM_STATE.qdrantGrpcPort),
    qdrantPreferGrpc: readBoolean(record, 'qdrantPreferGrpc'),
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
    weaviateScheme: readString(record, 'weaviateScheme', DEFAULT_PROVIDER_FORM_STATE.weaviateScheme),
    weaviateHost: readString(record, 'weaviateHost'),
    weaviatePort: readString(record, 'weaviatePort', DEFAULT_PROVIDER_FORM_STATE.weaviatePort),
    weaviateConsistencyLevelStrong: readBoolean(record, 'weaviateConsistencyLevelStrong'),
    milvusHost: readString(record, 'milvusHost'),
    milvusPort: readString(record, 'milvusPort', DEFAULT_PROVIDER_FORM_STATE.milvusPort),
    milvusDatabaseName: readString(record, 'milvusDatabaseName', DEFAULT_PROVIDER_FORM_STATE.milvusDatabaseName),
    milvusSecure: readBoolean(record, 'milvusSecure'),
    milvusFlushOnWrite: readBoolean(record, 'milvusFlushOnWrite'),
  }
}

function buildSummaryItems(form: ProviderFormState): SummaryItem[] {
  const items: SummaryItem[] = [
    { label: 'LLM provider', value: form.llmProvider.trim() || 'Not configured' },
    { label: 'Embedding provider', value: form.embeddingProvider.trim() || 'Not configured' },
    { label: 'Vector strategy', value: form.vectorStrategy.trim() || 'Not configured' },
    { label: 'Runtime profile', value: form.runtimeProfile.trim() || 'Not configured' },
    { label: 'Connector profile', value: form.connectorProfile.trim() || 'Not configured' },
  ]

  if (form.llmProvider === 'azure' || form.embeddingProvider === 'azure') {
    items.push({ label: 'Azure endpoint', value: form.azureEndpoint.trim() || 'Not configured' })
    items.push({ label: 'Azure API version', value: form.azureApiVersion.trim() || 'Not configured' })
  }
  if (form.llmProvider === 'openai' || form.embeddingProvider === 'openai') {
    items.push({ label: 'OpenAI base URL', value: form.openaiBaseUrl.trim() || 'Default provider endpoint' })
    items.push({ label: 'OpenAI LLM model', value: form.openaiModel.trim() || 'Default' })
    items.push({ label: 'OpenAI embedding model', value: form.openaiEmbeddingModel.trim() || 'Default' })
    items.push({ label: 'OpenAI embedding dimensions', value: form.openaiEmbeddingDimensions.trim() || '1536' })
  }
  if (form.llmProvider === 'anthropic') {
    items.push({ label: 'Anthropic base URL', value: form.anthropicBaseUrl.trim() || 'Default provider endpoint' })
    items.push({ label: 'Anthropic model', value: form.anthropicModel.trim() || 'Default' })
  }
  if (form.llmProvider === 'azure') {
    items.push({ label: 'Azure LLM deployment', value: form.azureDeploymentName.trim() || 'Not configured' })
  }
  if (form.embeddingProvider === 'azure') {
    items.push({ label: 'Azure embedding deployment', value: form.azureEmbeddingDeploymentName.trim() || 'Not configured' })
  }
  if (form.llmProvider === 'cohere' || form.embeddingProvider === 'cohere') {
    items.push({ label: 'Cohere base URL', value: form.cohereBaseUrl.trim() || 'Default provider endpoint' })
    items.push({ label: 'Cohere LLM model', value: form.cohereModel.trim() || 'Default' })
    items.push({ label: 'Cohere embedding model', value: form.cohereEmbeddingModel.trim() || 'Default' })
  }
  if (form.llmProvider === 'gemini' || form.embeddingProvider === 'gemini') {
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
    items.push({ label: 'Qdrant host', value: form.qdrantHost.trim() || 'Not configured' })
    items.push({ label: 'Qdrant port', value: form.qdrantPort.trim() || '6333' })
    items.push({ label: 'Qdrant gRPC port', value: form.qdrantGrpcPort.trim() || '6334' })
    items.push({ label: 'Prefer gRPC', value: String(form.qdrantPreferGrpc) })
  }
  if (form.vectorStrategy === 'pinecone') {
    items.push({ label: 'Pinecone environment', value: form.pineconeEnvironment.trim() || 'Not configured' })
    items.push({ label: 'Pinecone index', value: form.pineconeIndexName.trim() || 'Derived from API host or not configured' })
    items.push({ label: 'Pinecone API host', value: form.pineconeApiHost.trim() || 'Not configured' })
    items.push({ label: 'Pinecone dimensions', value: form.pineconeDimensions.trim() || '1536' })
  }
  if (form.vectorStrategy === 'weaviate') {
    items.push({ label: 'Weaviate scheme', value: form.weaviateScheme.trim() || 'https' })
    items.push({ label: 'Weaviate host', value: form.weaviateHost.trim() || 'Not configured' })
    items.push({ label: 'Weaviate port', value: form.weaviatePort.trim() || '443' })
    items.push({ label: 'Strong consistency', value: String(form.weaviateConsistencyLevelStrong) })
  }
  if (form.vectorStrategy === 'milvus') {
    items.push({ label: 'Milvus host', value: form.milvusHost.trim() || 'Not configured' })
    items.push({ label: 'Milvus port', value: form.milvusPort.trim() || '19530' })
    items.push({ label: 'Milvus database', value: form.milvusDatabaseName.trim() || 'default' })
    items.push({ label: 'Secure transport', value: String(form.milvusSecure) })
    items.push({ label: 'Flush on write', value: String(form.milvusFlushOnWrite) })
  }

  return items
}

function providerFormsEqual(left: ProviderFormState, right: ProviderFormState): boolean {
  return providerFormKeys.every((key) => left[key] === right[key])
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

  const saveMutation = useMutation({
    mutationFn: ({ draftId, providerConfig }: { draftId: string; providerConfig: unknown }) =>
      updateDeploymentDraft(draftId, { providerConfig }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['deployment-draft', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-validation'] }),
        queryClient.invalidateQueries({ queryKey: ['deployment-workspace', selectedDeploymentId] }),
        queryClient.invalidateQueries({ queryKey: ['deployments'] }),
      ])
    },
  })

  const handleFieldChange = <K extends keyof ProviderFormState>(key: K, value: ProviderFormState[K]) => {
    setFormState((previous) => ({
      ...previous,
      [key]: value,
    }))
  }

  const handleSave = () => {
    if (!draftQuery.data) {
      return
    }

    const nextConfig = cloneJson(
      isRecord(draftQuery.data.providerConfig) ? draftQuery.data.providerConfig : {},
    ) as Record<string, unknown>

    providerFormKeys.forEach((key) => {
      const value = formState[key]
      nextConfig[key] = typeof value === 'string' ? value.trim() : value
    })

    saveMutation.mutate({
      draftId: draftQuery.data.id,
      providerConfig: nextConfig,
    })
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

                        {(formState.llmProvider === 'openai' || formState.embeddingProvider === 'openai') ? (
                          <>
                            <Grid item xs={12}>
                              <TextField
                                fullWidth
                                label="OpenAI base URL"
                                value={formState.openaiBaseUrl}
                                onChange={(event) => handleFieldChange('openaiBaseUrl', event.target.value)}
                                helperText="Optional. Use for private gateways or OpenAI-compatible routed endpoints."
                              />
                            </Grid>
                            {formState.llmProvider === 'openai' ? (
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
                          </>
                        ) : null}

                        {formState.llmProvider === 'anthropic' ? (
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
                          </>
                        ) : null}

                        {(formState.llmProvider === 'azure' || formState.embeddingProvider === 'azure') ? (
                          <>
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
                            {formState.llmProvider === 'azure' ? (
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
                          </>
                        ) : null}

                        {(formState.llmProvider === 'cohere' || formState.embeddingProvider === 'cohere') ? (
                          <>
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
                          </>
                        ) : null}

                        {(formState.llmProvider === 'gemini' || formState.embeddingProvider === 'gemini') ? (
                          <>
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
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="Qdrant host"
                                value={formState.qdrantHost}
                                onChange={(event) => handleFieldChange('qdrantHost', event.target.value)}
                                helperText="Required. Hostname or internal address for the target Qdrant cluster."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Qdrant port"
                                value={formState.qdrantPort}
                                onChange={(event) => handleFieldChange('qdrantPort', event.target.value)}
                                helperText="Default 6333"
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Qdrant gRPC port"
                                value={formState.qdrantGrpcPort}
                                onChange={(event) => handleFieldChange('qdrantGrpcPort', event.target.value)}
                                helperText="Default 6334"
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
                          </>
                        ) : null}

                        {formState.vectorStrategy === 'pinecone' ? (
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
                                label="Pinecone index"
                                value={formState.pineconeIndexName}
                                onChange={(event) => handleFieldChange('pineconeIndexName', event.target.value)}
                                helperText="Required unless the platform can derive it from the API host."
                              />
                            </Grid>
                            <Grid item xs={12} md={8}>
                              <TextField
                                fullWidth
                                label="Pinecone API host"
                                value={formState.pineconeApiHost}
                                onChange={(event) => handleFieldChange('pineconeApiHost', event.target.value)}
                                helperText="Optional. Use when you want to target a fully qualified Pinecone host."
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
                              <TextField
                                fullWidth
                                label="Pinecone project ID"
                                value={formState.pineconeProjectId}
                                onChange={(event) => handleFieldChange('pineconeProjectId', event.target.value)}
                                helperText="Optional. Used when the host should be composed from index, project, and environment."
                              />
                            </Grid>
                          </>
                        ) : null}

                        {formState.vectorStrategy === 'weaviate' ? (
                          <>
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
                            <Grid item xs={12} md={6}>
                              <TextField
                                fullWidth
                                label="Milvus host"
                                value={formState.milvusHost}
                                onChange={(event) => handleFieldChange('milvusHost', event.target.value)}
                                helperText="Required. Hostname of the Milvus cluster."
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Milvus port"
                                value={formState.milvusPort}
                                onChange={(event) => handleFieldChange('milvusPort', event.target.value)}
                                helperText="Default 19530"
                              />
                            </Grid>
                            <Grid item xs={12} md={3}>
                              <TextField
                                fullWidth
                                label="Milvus database"
                                value={formState.milvusDatabaseName}
                                onChange={(event) => handleFieldChange('milvusDatabaseName', event.target.value)}
                                helperText="Default default"
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
