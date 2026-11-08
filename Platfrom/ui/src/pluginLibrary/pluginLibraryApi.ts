import { platformRequest } from '../api/platformApi'
import { parseExactJson, parseExactObject, stringifyExactJson } from '../lib/losslessJson'
import type { PluginDetail, PluginLibraryApi, PluginSummary } from './types'

type Manifest = Record<string, unknown>
type Version = { id: string; version: string; status: string; manifest: Manifest; createdAt: string }
type SavedInstall = { id: string; deploymentId: string; deploymentName: string; pluginVersion: string; status: string }
type AppliedUse = { deploymentId: string; targetProfileId: string; releaseId: string; pluginVersion: string }
type Summary = {
  id: string; ownerCustomerId: string; visibility: string; displayName: string; status: string
  manifest: Manifest; revision: number; canManage: boolean; versions: Version[]
  catalogueFamily: string; catalogueAudience: string; adapterContract: string
  installs: { savedCount: number; appliedCount: number; saved: SavedInstall[]; applied: AppliedUse[] }
}
type Library = { customerId: string; canManage: boolean; plugins: Summary[] }

const path = (suffix: string, customerId?: string) => `/api/customer/plugin-library${suffix}${customerId ? `?customerId=${encodeURIComponent(customerId)}` : ''}`
const idPath = (id: string) => `/${encodeURIComponent(id)}`
const read = <T>(suffix: string, customerId?: string) => platformRequest<T>(path(suffix, customerId), undefined, 'default', parseExactJson)
const command = <T>(suffix: string, method: string, payload: unknown, customerId?: string) => platformRequest<T>(path(suffix, customerId), {
  method, body: stringifyExactJson(payload),
}, 'default', parseExactJson)

function toSummary(value: Summary): PluginSummary {
  return {
    pluginId: value.id, displayName: value.displayName,
    description: typeof value.manifest.description === 'string' ? value.manifest.description : '',
    status: value.status, latestVersion: value.versions[0]?.version, draftRevision: value.revision,
    sourceKind: value.adapterContract || value.catalogueFamily,
    savedInstallations: value.installs.savedCount, appliedUses: value.installs.appliedCount,
  }
}
function toDetail(value: Summary): PluginDetail {
  return {
    ...toSummary(value), customerId: value.ownerCustomerId, canManage: value.canManage,
    manifestJson: stringifyExactJson(value.manifest, 2), versions: value.versions.map(({ version, createdAt }) => ({ version, createdAt })),
    usage: [
      ...value.installs.saved.map(install => ({ deploymentId: install.deploymentId, brainName: install.deploymentName,
        version: install.pluginVersion, state: `Saved / ${install.status}`, returnTo: `/smart-brains/${encodeURIComponent(install.deploymentId)}/capabilities` })),
      ...value.installs.applied.map(use => ({ deploymentId: use.deploymentId, version: use.pluginVersion,
        state: 'Applied release', returnTo: `/smart-brains/${encodeURIComponent(use.deploymentId)}/capabilities?${new URLSearchParams({ targetProfileId: use.targetProfileId, releaseId: use.releaseId })}` })),
    ],
  }
}

export const pluginLibraryApi: PluginLibraryApi = {
  async list(customerId) {
    const result = await read<Library>('', customerId)
    return { customerId: result.customerId, canManage: result.canManage, plugins: result.plugins.map(toSummary) }
  },
  async get(id, customerId) { return toDetail(await read<Summary>(idPath(id), customerId)) },
  async create(input, customerId) {
    return toDetail(await command<Summary>('', 'POST', { manifest: parseExactObject(input.manifestJson) }, customerId))
  },
  async save(id, input, customerId) {
    return toDetail(await command<Summary>(idPath(id), 'PUT', { manifest: parseExactObject(input.manifestJson), expectedRevision: input.expectedRevision }, customerId))
  },
  async check(id, input, customerId) {
    return command(`${idPath(id)}/check`, 'POST', { manifest: parseExactObject(input.manifestJson), expectedRevision: input.expectedRevision }, customerId)
  },
  async createVersion(id, input, customerId) {
    return toDetail(await command<Summary>(`${idPath(id)}/versions`, 'POST', { manifest: parseExactObject(input.manifestJson), expectedRevision: input.expectedRevision }, customerId))
  },
  async getVersion(id, version, customerId) {
    const value = await read<Version>(`${idPath(id)}/versions/${encodeURIComponent(version)}`, customerId)
    return { manifestJson: stringifyExactJson(value.manifest, 2), version: value.version, createdAt: value.createdAt }
  },
  async archive(id, input, customerId) {
    return toDetail(await command<Summary>(`${idPath(id)}/archive`, 'POST', input, customerId))
  },
}
