export type PluginIssue = { path: string; message: string; severity?: string }
export type PluginCheck = { valid: boolean; issues: PluginIssue[] }
export type PluginVersion = { version: string; createdAt?: string }
export type PluginUsage = {
  deploymentId: string
  brainName?: string
  version: string
  state: string
  returnTo?: string
}
export type PluginSummary = {
  pluginId: string
  displayName: string
  description?: string
  status: string
  latestVersion?: string
  draftRevision: number
  sourceKind?: string
  savedInstallations?: number
  appliedUses?: number | null
}
export type PluginDetail = PluginSummary & {
  customerId: string
  customerName?: string
  canManage: boolean
  manifestJson: string
  versions: PluginVersion[]
  usage: PluginUsage[]
}
export type PluginList = { customerId: string; customerName?: string; canManage: boolean; plugins: PluginSummary[] }
export type PluginLibraryApi = {
  list(customerId?: string): Promise<PluginList>
  get(pluginId: string, customerId?: string): Promise<PluginDetail>
  create(input: { manifestJson: string }, customerId?: string): Promise<PluginDetail>
  save(pluginId: string, input: { manifestJson: string; expectedRevision: number }, customerId?: string): Promise<PluginDetail>
  check(pluginId: string, input: { manifestJson: string; expectedRevision: number }, customerId?: string): Promise<PluginCheck>
  createVersion(pluginId: string, input: { manifestJson: string; expectedRevision: number; version: string }, customerId?: string): Promise<PluginDetail>
  getVersion(pluginId: string, version: string, customerId?: string): Promise<{ manifestJson: string; version: string; createdAt?: string }>
  archive(pluginId: string, input: { expectedRevision: number }, customerId?: string): Promise<PluginDetail>
}
