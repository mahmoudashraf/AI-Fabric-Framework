#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const [draftPath, variant] = process.argv.slice(2);
if (!draftPath || !['generic', 'loomai', 'acme-cloud'].includes(variant) || process.argv.length !== 4) {
  throw new Error('Usage: node scripts/compose-draft.mjs <saved-GET-draft.json> <generic|loomai|acme-cloud>');
}
const draft = JSON.parse(readFileSync(resolve(draftPath), 'utf8'));
if (!draft.behaviorConfig || draft.behaviorConfig.type !== 'CONVERSATIONAL') {
  throw new Error('Read an owned CONVERSATIONAL deployment draft before composing an overlay.');
}
if (!draft.providerConfig || typeof draft.providerConfig !== 'object' || Array.isArray(draft.providerConfig)) {
  throw new Error('The saved draft must include its existing providerConfig so provider settings are preserved.');
}
const read = name => JSON.parse(readFileSync(resolve(root, 'overlays', variant, name), 'utf8'));
const template = JSON.parse(readFileSync(resolve(root, 'manifests/ai-support-workspace.1.0.1.manifest.json'), 'utf8')).contributions.template;
const behaviorConfig = structuredClone(draft.behaviorConfig);
behaviorConfig.specialistAssistance = variant === 'generic' ? template.deploymentBehavior.specialistAssistance : read('specialist-assistance.json');
behaviorConfig.specialistAssistance.enabled = true;
behaviorConfig.executionExtensions = [...new Set([...(behaviorConfig.executionExtensions ?? []), 'SPECIALIST_ASSISTANCE'])];
const providerConfig = structuredClone(draft.providerConfig);
const settings = JSON.parse(readFileSync(resolve(root, 'fixtures/orchestration-settings.json'), 'utf8'));
providerConfig.orchestrationModeOverrides ??= {};
const executor = providerConfig.orchestrationModeOverrides.executor ?? {};
providerConfig.orchestrationModeOverrides.executor = {
  ...executor, ...settings.executor,
  readActionResolution: { ...(executor.readActionResolution ?? {}), ...settings.executor.readActionResolution },
};
const shellConfig = variant === 'generic'
  ? { greeting: template.shell.greeting, modules: [], cards: [], starterPrompts: template.shell.starterPrompts, defaultConversationMode: 'executor' }
  : read('shell-config.json');
// Normal draft sections only; preserve provider choices and never replace security, ownership or connector actions.
process.stdout.write(JSON.stringify({ behaviorConfig, shellConfig, providerConfig }, null, 2) + '\n');
