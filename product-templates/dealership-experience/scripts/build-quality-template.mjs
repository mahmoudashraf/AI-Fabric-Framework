#!/usr/bin/env node
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const defaults = JSON.parse(readFileSync(new URL('../fixtures/northfield-quality-defaults.json', import.meta.url), 'utf8'))
const groupKeys = ['tone', 'answerBreadth', 'answerLength', 'nextSteps']

function record(value, path) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${path} must be an object.`)
  }
  return value
}

function optionalRecord(parent, key, path) {
  return Object.hasOwn(parent, key) ? record(parent[key], path) : {}
}

function managedBehaviour(catalogue) {
  record(catalogue, 'Behaviour catalogue')
  const selection = defaults.aiBehaviour
  if (catalogue.schemaVersion !== selection.schemaVersion || catalogue.revision !== selection.revision) {
    throw new Error('Unsupported behaviour catalogue schema or revision.')
  }
  if (!Array.isArray(catalogue.groups)) throw new Error('Behaviour catalogue groups must be an array.')
  const result = { schemaVersion: selection.schemaVersion, revision: selection.revision }
  for (const key of groupKeys) {
    const groups = catalogue.groups.filter(group => group?.key === key)
    if (groups.length !== 1 || !Array.isArray(groups[0].options)) {
      throw new Error(`Behaviour catalogue requires one ${key} group with options.`)
    }
    const choices = groups[0].options.filter(option => option?.id === selection.selections[key])
    if (choices.length !== 1 || typeof choices[0].instructions !== 'string' || !choices[0].instructions.trim()) {
      throw new Error(`Behaviour catalogue requires one ${key} selection with instructions.`)
    }
    if (Object.hasOwn(choices[0], 'revision') && choices[0].revision !== selection.revision) {
      throw new Error('Unsupported behaviour catalogue option revision.')
    }
    result[key] = {
      presetId: selection.selections[key],
      revision: selection.revision,
      instructions: choices[0].instructions,
    }
  }
  return result
}

function validateExistingBehaviour(promptConfig) {
  if (!Object.hasOwn(promptConfig, 'aiBehaviour')) return
  const metadata = record(promptConfig.aiBehaviour, 'Template promptConfig.aiBehaviour')
  if (metadata.schemaVersion !== defaults.aiBehaviour.schemaVersion || metadata.revision !== defaults.aiBehaviour.revision) {
    throw new Error('Unsupported existing managed behaviour schema or revision.')
  }
  for (const key of groupKeys) {
    const profile = record(metadata[key], `Template promptConfig.aiBehaviour.${key}`)
    if (profile.revision !== defaults.aiBehaviour.revision
      || typeof profile.presetId !== 'string' || typeof profile.instructions !== 'string') {
      throw new Error('Unsupported existing managed behaviour profile.')
    }
  }
}

/** Builds a new template revision; never accepts or updates a customer draft. */
export function buildDealershipQualityTemplate(manifest, catalogue) {
  record(manifest, 'Current template manifest')
  const target = defaults.templates.find(template => template.pluginId === manifest.pluginId)
  if (!target || manifest.pluginType !== 'TEMPLATE' || manifest.schemaVersion !== 1) {
    throw new Error('Only the three approved schema-1 dealership TEMPLATE manifests are supported.')
  }
  if (manifest.version !== target.currentVersion) {
    throw new Error('Current template version differs from the reviewed baseline; review it before building another candidate.')
  }
  const template = record(record(manifest.contributions, 'Manifest contributions').template, 'Template contribution')
  const behaviour = record(template.deploymentBehavior, 'Template deploymentBehavior')
  if (behaviour.type !== 'CONVERSATIONAL' || behaviour.contractVersion !== 1) {
    throw new Error('Only the current conversational contract revision 1 is supported.')
  }
  const provider = optionalRecord(template, 'providerConfig', 'Template providerConfig')
  const modes = optionalRecord(provider, 'orchestrationModeOverrides', 'Template orchestrationModeOverrides')
  const executor = optionalRecord(modes, 'executor', 'Template executor overrides')
  const prompt = optionalRecord(template, 'promptConfig', 'Template promptConfig')
  validateExistingBehaviour(prompt)
  const selectedBehaviour = managedBehaviour(catalogue)
  const candidate = structuredClone(manifest)
  const candidateTemplate = candidate.contributions.template
  const executorDefaults = defaults.providerConfig.orchestrationModeOverrides.executor
  const mergedExecutor = { ...executor, knowledgeBaseOverviewEnabled: executorDefaults.knowledgeBaseOverviewEnabled }
  for (const key of ['rag', 'semanticRecovery', 'readActionResolution']) {
    mergedExecutor[key] = { ...optionalRecord(executor, key, `Template executor.${key}`), ...executorDefaults[key] }
  }
  candidateTemplate.providerConfig = structuredClone({
    ...provider,
    orchestrationModeOverrides: { ...modes, executor: mergedExecutor },
  })
  candidateTemplate.promptConfig = {
    ...structuredClone(prompt),
    ...defaults.promptConfig,
    aiBehaviour: selectedBehaviour,
  }
  candidate.version = target.candidateVersion
  return candidate
}

function readSavedManifest(value) {
  record(value, 'Saved template input')
  if (Object.hasOwn(value, 'pluginType')) return value
  const saved = record(value.version, 'Saved published template version')
  const manifest = record(saved.manifest, 'Saved published template manifest')
  if (saved.status !== 'PUBLISHED' || saved.pluginId !== manifest.pluginId || saved.version !== manifest.version) {
    throw new Error('Saved published version metadata does not match its template manifest.')
  }
  return manifest
}

function main() {
  const args = process.argv.slice(2)
  if (args.length !== 2) {
    throw new Error('Usage: node scripts/build-quality-template.mjs <saved-template.json> <saved-behaviour-catalogue.json>')
  }
  const input = JSON.parse(readFileSync(resolve(args[0]), 'utf8'))
  const catalogue = JSON.parse(readFileSync(resolve(args[1]), 'utf8'))
  process.stdout.write(`${JSON.stringify(buildDealershipQualityTemplate(readSavedManifest(input), catalogue), null, 2)}\n`)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main() } catch (error) {
    console.error(error instanceof Error ? error.message : 'Unable to build template candidate.')
    process.exitCode = 1
  }
}
