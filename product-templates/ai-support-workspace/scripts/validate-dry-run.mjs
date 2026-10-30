#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repository = resolve(root, '..', '..');
if (process.argv.slice(2).some(value => value !== '--dry-run')) {
  throw new Error('Usage: node scripts/validate-dry-run.mjs [--dry-run]. This tool never sends API requests.');
}
const read = path => JSON.parse(readFileSync(resolve(root, path), 'utf8'));
const catalog = read('catalog.json');
const requests = catalog.entries.map(entry => ({
  method: 'POST',
  path: '/api/marketplace/publishers/{verifiedPublisherId}/submissions',
  body: { pluginSlug: entry.pluginSlug, releaseChannel: 'EARLY_ACCESS', manifest: read(entry.manifest) },
  usage: entry.usage,
}));
const scriptCheck = spawnSync(process.execPath, ['--test', resolve(root, 'scripts/compose-draft.test.mjs'), resolve(root, 'fixtures/service/server.test.mjs')], {
  encoding: 'utf8', maxBuffer: 1024 * 1024,
});
if (scriptCheck.status !== 0) {
  process.stderr.write(scriptCheck.stdout ?? '');
  process.stderr.write(scriptCheck.stderr ?? '');
  throw new Error('Offline draft-composition checks failed. No API call was made.');
}
// Use the real control-plane validator offline; a JSON parse alone is not contract qualification.
const check = spawnSync('mvn', [
  '-o', '-q', '-f', resolve(repository, 'Platfrom/backend/pom.xml'),
  '-Dtest=SupportWorkspaceProductTemplateTest,SpecialistAssistanceVerificationPackTest', `-Dsupport.template.root=${root}`, 'test',
], { cwd: repository, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 });
if (check.status !== 0) {
  process.stderr.write(check.stdout ?? '');
  process.stderr.write(check.stderr ?? '');
  throw new Error(`Marketplace contract validation did not pass: ${check.error?.message ?? check.status}. No API call was made.`);
}
process.stdout.write(JSON.stringify({
  dryRun: true,
  networkApiCalls: 0,
  validatedBy: 'MarketplaceManifestService + SpecialistAssistanceCompiler via SupportWorkspaceProductTemplateTest',
  draftCompositionTests: 4,
  simulatedServiceTests: 2,
  status: catalog.status,
  requests,
}, null, 2) + '\n');
