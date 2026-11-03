#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const editable = new Set(['workspaceName', 'brandColor', 'topics', 'teamVisible', 'humanSupportEnabled', 'ticketingEnabled']);

export function composeSupportExperience(current, preferences) {
  if (current?.experiencePackCode !== 'support' || current.experiencePackVersion !== '1.0.0'
      || current.configuration?.schemaVersion !== 'loomai-support-experience-config-v1'
      || !Number.isSafeInteger(current.rowVersion) || current.rowVersion < 0) {
    throw new Error('Read an owned current support installation with its rowVersion before composing a draft update.');
  }
  if (!preferences || Array.isArray(preferences) || typeof preferences !== 'object'
      || Object.keys(preferences).some(key => !editable.has(key))) throw new Error('Unknown support appearance/preference field.');
  const config = structuredClone(current.configuration);
  if (preferences.workspaceName !== undefined) {
    if (typeof preferences.workspaceName !== 'string' || !preferences.workspaceName.trim() || preferences.workspaceName.trim().length > 120) {
      throw new Error('workspaceName must be non-empty text up to 120 characters.');
    }
    config.app.assistantLabel = preferences.workspaceName.trim();
  }
  if (preferences.brandColor !== undefined) {
    if (!/^#[a-fA-F0-9]{6}$/.test(preferences.brandColor)) throw new Error('brandColor must be a six-digit hex colour.');
    config.theme = { ...config.theme, primaryColor: preferences.brandColor };
  }
  if (preferences.topics !== undefined) {
    const codes = new Set();
    if (!Array.isArray(preferences.topics) || preferences.topics.length > 20 || preferences.topics.some(topic => {
      if (!topic || Object.keys(topic).some(key => !['code', 'label'].includes(key))
          || typeof topic.code !== 'string' || !/^[a-z0-9][a-z0-9-]{0,63}$/.test(topic.code)
          || codes.has(topic.code) || typeof topic.label !== 'string' || !topic.label.trim() || topic.label.length > 100) return true;
      codes.add(topic.code); return false;
    })) throw new Error('topics must contain at most twenty unique bounded code/label pairs.');
    config.support.topics = structuredClone(preferences.topics);
  }
  for (const field of ['teamVisible', 'humanSupportEnabled', 'ticketingEnabled']) {
    if (preferences[field] !== undefined && typeof preferences[field] !== 'boolean') throw new Error(`${field} must be boolean.`);
  }
  if (preferences.teamVisible !== undefined) config.support.teamVisible = preferences.teamVisible;
  if (preferences.humanSupportEnabled !== undefined) config.capabilities.handover = preferences.humanSupportEnabled;
  if (preferences.ticketingEnabled !== undefined) {
    config.capabilities.tickets = preferences.ticketingEnabled;
  }
  const fields = ['consumerId', 'experiencePackCode', 'experiencePackVersion', 'connectionMode',
    'connectionProfileCode', 'connectionProfileVersion', 'connectionConfiguration', 'allowedOrigins',
    'deliveryMode', 'appSlug', 'interactionPolicy', 'authProfileCode', 'authProfileVersion', 'authConfiguration', 'rowVersion'];
  const update = Object.fromEntries(fields.map(field => [field, structuredClone(current[field])]));
  return { ...update, displayName: preferences.workspaceName?.trim() ?? current.displayName,
    customDomains: (current.domains ?? []).filter(domain => domain.type === 'CUSTOMER_CUSTOM').map(domain => domain.hostname),
    configuration: config };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [installation, preferences, ...extra] = process.argv.slice(2);
  if (!installation || !preferences || extra.length) throw new Error('Usage: node scripts/compose-support-experience.mjs <saved-installation.json> <preferences.json>');
  const read = file => JSON.parse(readFileSync(resolve(file), 'utf8'));
  process.stdout.write(`${JSON.stringify(composeSupportExperience(read(installation), read(preferences)), null, 2)}\n`);
}
