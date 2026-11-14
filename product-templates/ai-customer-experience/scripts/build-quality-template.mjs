#!/usr/bin/env node
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { resolveManagedBehaviour } from '../../shared/managed-behaviour.mjs'
import { readConversationalQualityDefaults } from '../../shared/conversational-quality-defaults.mjs'

const defaults = JSON.parse(readFileSync(new URL('../fixtures/quality-defaults.json', import.meta.url), 'utf8'))
const quality = readConversationalQualityDefaults()
const { planningMode, maxIterations, ragCooperationMode,
  maxPlannerContextChars, maxActionEvidenceCharsPerAction, maxActionEvidenceItems } = quality.readActionResolution
const readBudgets = { maxPlannerContextChars, maxActionEvidenceCharsPerAction, maxActionEvidenceItems }
const object = (value, name) => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${name} must be an object.`)
  return value
}
const optional = (parent, key) => Object.hasOwn(parent, key) ? object(parent[key], key) : {}

function largerBudgets(current, minimums) {
  const result = { ...current }
  for (const [key, minimum] of Object.entries(minimums)) {
    if (Object.hasOwn(current, key) && (!Number.isSafeInteger(current[key]) || current[key] < 1)) {
      throw new Error(`Invalid existing evidence budget: ${key}`)
    }
    result[key] = Math.max(current[key] ?? minimum, minimum)
  }
  return result
}

export function buildCustomerExperienceQualityTemplate(manifest, catalogue) {
  object(manifest, 'Published template manifest')
  if (manifest.schemaVersion !== 1 || manifest.pluginType !== 'TEMPLATE' || manifest.pluginId !== defaults.template.pluginId
    || manifest.version !== defaults.template.currentVersion) throw new Error('Review the current AI Customer Experience template baseline before building.')
  const template = object(object(manifest.contributions, 'Contributions').template, 'Template')
  const behaviour = object(template.deploymentBehavior, 'Deployment behaviour')
  if (behaviour.type !== 'CONVERSATIONAL' || behaviour.contractVersion !== 1) throw new Error('Current conversational contract required.')
  const prompt = optional(template, 'promptConfig')
  const provider = optional(template, 'providerConfig')
  const modes = optional(provider, 'orchestrationModeOverrides')
  const executor = optional(modes, 'executor')
  const reads = optional(executor, 'readActionResolution')
  const profiles = resolveManagedBehaviour(catalogue, { ...defaults.aiBehaviour, selections: quality.behaviourSelections })
  if (Object.hasOwn(prompt, 'aiBehaviour')) {
    const existing = object(prompt.aiBehaviour, 'Existing managed profiles')
    if (existing.schemaVersion !== profiles.schemaVersion || existing.revision !== profiles.revision) throw new Error('Unsupported existing managed profiles.')
    for (const key of ['tone', 'answerBreadth', 'answerLength', 'nextSteps']) {
      const profile = object(existing[key], `Existing ${key} profile`)
      if (profile.revision !== profiles.revision || typeof profile.presetId !== 'string' || typeof profile.instructions !== 'string') {
        throw new Error('Unsupported existing managed profile contract.')
      }
      if (key === 'answerBreadth' || profile.presetId === 'custom') profiles[key] = structuredClone(profile)
    }
  }
  const candidate = structuredClone(manifest)
  const next = candidate.contributions.template
  next.promptConfig = {
    intentExtractionPrompt: quality.intentExtractionPrompt,
    ...largerBudgets(structuredClone(prompt), quality.promptBudgets),
    aiBehaviour: profiles,
  }
  next.providerConfig = {
    openaiModel: quality.openaiModel,
    openaiMaxTokens: quality.openaiMaxTokens,
    ...structuredClone(provider),
    orchestrationModeOverrides: {
      ...structuredClone(modes),
      executor: {
        knowledgeBaseOverviewEnabled: quality.knowledgeBaseOverviewEnabled,
        ...structuredClone(executor),
        readActionResolution: {
          ...largerBudgets(structuredClone(reads), readBudgets),
          planningMode, maxIterations, ragCooperationMode,
        },
      },
    },
  }
  candidate.version = defaults.template.candidateVersion
  return candidate
}

function main() {
  const args = process.argv.slice(2)
  if (args.length !== 2) throw new Error('Usage: node build-quality-template.mjs <published-template.json> <behaviour-catalogue.json>')
  const input = object(JSON.parse(readFileSync(resolve(args[0]), 'utf8')), 'Saved template')
  const saved = Object.hasOwn(input, 'pluginType') ? null : object(input.version, 'Published version')
  if (saved && (saved.status !== 'PUBLISHED' || saved.pluginId !== saved.manifest?.pluginId || saved.version !== saved.manifest?.version)) {
    throw new Error('Published template metadata mismatch.')
  }
  const result = buildCustomerExperienceQualityTemplate(saved?.manifest ?? input, JSON.parse(readFileSync(resolve(args[1]), 'utf8')))
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main() } catch (error) { console.error(error.message); process.exitCode = 1 }
}
