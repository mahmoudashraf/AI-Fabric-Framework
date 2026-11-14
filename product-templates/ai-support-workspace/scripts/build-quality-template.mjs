#!/usr/bin/env node
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { resolveManagedBehaviour } from '../../shared/managed-behaviour.mjs'
import { readConversationalQualityDefaults } from '../../shared/conversational-quality-defaults.mjs'

const pluginIds = ['mkp-template-ai-support-workspace', 'mkp-template-loomai-support-workspace']
const quality = readConversationalQualityDefaults()
const { planningMode, maxIterations, ragCooperationMode,
  maxPlannerContextChars, maxActionEvidenceCharsPerAction, maxActionEvidenceItems } = quality.readActionResolution
const readBudgets = { maxPlannerContextChars, maxActionEvidenceCharsPerAction, maxActionEvidenceItems }
const object = (value, name) => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${name} must be an object.`)
  return value
}
const optional = (parent, key) => Object.hasOwn(parent, key) ? object(parent[key], key) : {}

function minimumBudgets(current, minimums) {
  const result = structuredClone(current)
  for (const [key, minimum] of Object.entries(minimums)) {
    if (Object.hasOwn(current, key) && (!Number.isSafeInteger(current[key]) || current[key] < 1)) {
      throw new Error(`Invalid existing evidence budget: ${key}`)
    }
    result[key] = Math.max(current[key] ?? minimum, minimum)
  }
  return result
}

export function buildSupportQualityTemplate(manifest, catalogue) {
  object(manifest, 'Published template manifest')
  if (manifest.schemaVersion !== 1 || manifest.pluginType !== 'TEMPLATE' || !pluginIds.includes(manifest.pluginId)
    || manifest.version !== '1.2.0') throw new Error('Review the published Support 1.2.0 baseline before building.')
  const template = object(object(manifest.contributions, 'Contributions').template, 'Template')
  const behaviour = object(template.deploymentBehavior, 'Deployment behaviour')
  if (behaviour.type !== 'CONVERSATIONAL' || behaviour.contractVersion !== 1) throw new Error('Current conversational contract required.')
  const prompt = optional(template, 'promptConfig')
  const provider = optional(template, 'providerConfig')
  const modes = optional(provider, 'orchestrationModeOverrides')
  const executor = optional(modes, 'executor')
  const reads = optional(executor, 'readActionResolution')
  const profiles = resolveManagedBehaviour(catalogue, {
    schemaVersion: 'loomai-ai-behaviour-v1', revision: 1, selections: quality.behaviourSelections,
  })
  let managed = profiles
  if (Object.hasOwn(prompt, 'aiBehaviour')) {
    const existing = object(prompt.aiBehaviour, 'Existing managed profiles')
    if (existing.schemaVersion !== profiles.schemaVersion || existing.revision !== profiles.revision) {
      throw new Error('Unsupported existing managed profiles.')
    }
    managed = { ...structuredClone(existing), ...profiles }
    for (const key of Object.keys(quality.behaviourSelections)) {
      if (!Object.hasOwn(existing, key)) continue
      const profile = object(existing[key], `Existing ${key} profile`)
      if (profile.revision !== profiles.revision || typeof profile.presetId !== 'string' || typeof profile.instructions !== 'string') {
        throw new Error('Unsupported existing managed profile contract.')
      }
      if (key === 'answerBreadth' || profile.presetId === 'custom') managed[key] = structuredClone(profile)
    }
  }
  const candidate = structuredClone(manifest)
  const next = candidate.contributions.template
  next.promptConfig = {
    intentExtractionPrompt: quality.intentExtractionPrompt,
    ...minimumBudgets(prompt, quality.promptBudgets),
    aiBehaviour: managed,
  }
  next.providerConfig = {
    openaiMaxTokens: quality.openaiMaxTokens,
    ...structuredClone(provider),
    // Pin the reviewed mini alias; an explicitly different model remains custom.
    openaiModel: !Object.hasOwn(provider, 'openaiModel') || provider.openaiModel === 'gpt-5.4-mini'
      ? quality.openaiModel : provider.openaiModel,
    orchestrationModeOverrides: {
      ...structuredClone(modes),
      executor: {
        knowledgeBaseOverviewEnabled: quality.knowledgeBaseOverviewEnabled,
        ...structuredClone(executor),
        readActionResolution: {
          ...minimumBudgets(reads, readBudgets),
          planningMode, maxIterations, ragCooperationMode,
        },
      },
    },
  }
  candidate.version = '1.3.0'
  return candidate
}

function main() {
  const args = process.argv.slice(2)
  if (args.length !== 2) throw new Error('Usage: node build-quality-template.mjs <published-template.json> <behaviour-catalogue.json>; offline only')
  const input = object(JSON.parse(readFileSync(resolve(args[0]), 'utf8')), 'Saved template')
  const saved = Object.hasOwn(input, 'pluginType') ? null : object(input.version, 'Published version')
  if (saved && (saved.status !== 'PUBLISHED' || saved.pluginId !== saved.manifest?.pluginId || saved.version !== saved.manifest?.version)) {
    throw new Error('Published template metadata mismatch.')
  }
  const result = buildSupportQualityTemplate(saved?.manifest ?? input, JSON.parse(readFileSync(resolve(args[1]), 'utf8')))
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main() } catch (error) { console.error(error.message); process.exitCode = 1 }
}
