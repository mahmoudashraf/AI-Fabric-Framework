import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const root = fileURLToPath(new URL('../../', import.meta.url));
const catalogue = JSON.parse(readFileSync(new URL('./build-catalogue.json', import.meta.url), 'utf8'));
const workflow = readFileSync(path.join(root, '.github/workflows/publish-serving-app-images.yml'), 'utf8');
// Execute the production selection block with mocked Git/registry, without a second selector implementation.
const selection = workflow.split('// BEGIN CATALOGUE_SELECTION\n')[1]
  .split('// END CATALOGUE_SELECTION')[0].replace(/^ {12}/gm, '');
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
const runSelection = new AsyncFunction('require', 'process', 'core', selection);
const promotion = workflow.split('// BEGIN CHANNEL_PROMOTION\n')[1]
  .split('// END CHANNEL_PROMOTION')[0].replace(/^ {12}/gm, '');
const runPromotion = new AsyncFunction('require', 'process', 'core', 'github', promotion);
const platformSha = '1'.repeat(40);
const frameworkSha = '2'.repeat(40);
const beforeSha = '3'.repeat(40);
const localApps = catalogue.apps.filter(app => app.source === 'platform');
const frameworkApps = catalogue.apps.filter(app => app.source === 'framework');
const frameworkRootApps = new Set([
  'agentic-ai-action-resolver', 'deployment-knowledge-guard',
  'incident-investigation-room', 'mcp-operations-assistant',
]);

async function select({ event = 'workflow_dispatch', app = 'all', changed = [], exists = false,
  before = beforeSha, diffFails = false, sourceSha, registryFails = false,
  channelExists, frameworkRequest = 'main' } = {}) {
  const calls = [];
  const outputs = {};
  const messages = [];
  const summary = {
    addHeading() { return this; },
    addRaw(value) { messages.push(value); return this; },
    async write() {},
  };
  await runSelection(name => {
    if (name === 'node:fs') return { readFileSync: () => JSON.stringify(catalogue) };
    assert.equal(name, 'node:child_process');
    return {
      execFileSync(command, args, options) {
        calls.push({ command, args, options });
        if (command === 'docker') {
          if (registryFails) throw new Error('Registry unavailable');
          const available = args.at(-1).endsWith('-current') && channelExists !== undefined
            ? channelExists : (typeof exists === 'function' ? exists(args.at(-1)) : exists);
          if (available) return JSON.stringify({ digest: `sha256:${'a'.repeat(64)}` });
          throw Object.assign(new Error('no such manifest'), { stderr: 'no such manifest' });
        }
        assert.equal(command, 'git');
        if (args[0] === 'diff') {
          if (diffFails) throw new Error('Base commit unavailable');
          return changed.join('\0');
        }
        assert(['log', 'rev-parse'].includes(args[0]));
        return sourceSha ?? (options.cwd === 'framework-source' ? frameworkSha : platformSha);
      },
    };
  }, {
    env: {
      EVENT_NAME: event, SELECTED_APP: app, BEFORE_SHA: before,
      FRAMEWORK_SHA: frameworkRequest,
      GITHUB_REPOSITORY_OWNER: 'Loom-AI-Labs', GITHUB_REPOSITORY: 'Loom-AI-Labs/platform',
    },
  }, {
    setOutput: (name, value) => { outputs[name] = value; },
    info: value => messages.push(value), warning: value => messages.push(value), summary,
  });
  return { apps: JSON.parse(outputs.matrix).include, calls, messages };
}

test('catalogue contains five local apps and fifteen framework apps including the MCP companion', () => {
  assert.equal(catalogue.version, 1);
  assert.equal(localApps.length, 5);
  assert.equal(frameworkApps.length, 15);
  assert.equal(new Set(catalogue.apps.map(app => app.id)).size, 20);
  assert.equal(new Set(catalogue.apps.map(app => app.image)).size, 20);
  assert.equal(catalogue.sources.platform.repository, 'mahmoudashraf/AI-Fabric-Framework');
  assert.equal(catalogue.sources.platform.branch, 'Platform-V11');
  assert.equal(catalogue.sources.framework.repository, 'Loom-AI-Labs/ai-fabric-framework');
  assert.equal(catalogue.sources.framework.branch, 'main');
  assert.deepEqual(catalogue.sources.framework.inputPaths, ['examples/real-apps/']);
  assert(frameworkApps.some(app => app.id === 'ecommerce-store'));
  assert(frameworkApps.some(app => app.id === 'retrieval-connector-boundary-lab'));
  assert(frameworkApps.some(app => app.id === 'mcp-operations-reference-server'));
  assert.equal(localApps.find(app => app.id === 'external-vehicle-provider-simulator').image, 'loomai-serving-vehicle-simulator');
});

test('catalogue paths remain inside their checkout, with Dockerfiles covered by source inputs', () => {
  for (const app of catalogue.apps) {
    assert.match(app.id, /^[a-z0-9]+(?:-[a-z0-9]+)*$/);
    assert.match(app.image, /^[a-z0-9]+(?:-[a-z0-9]+)*$/);
    assert(catalogue.sources[app.source]);
    const inputs = [...(catalogue.sources[app.source].inputPaths || []), ...(app.inputPaths || [])];
    for (const entry of [app.context, app.dockerfile, ...inputs]) {
      assert(!path.isAbsolute(entry));
      assert(!entry.split('/').includes('..'));
      assert(!/[\n\r\0]/.test(entry));
    }
    assert(inputs.some(input => input.endsWith('/') ? app.dockerfile.startsWith(input) : app.dockerfile === input));
    assert(app.context === '.' || app.dockerfile.startsWith(`${app.context}/`));
    if (app.context === '.') assert(inputs.includes('.dockerignore'));
  }
});

test('local Dockerfiles exist and all COPY inputs resolve against the catalogue root context', () => {
  for (const app of localApps) {
    assert.equal(app.context, '.');
    const dockerfile = readFileSync(path.join(root, app.dockerfile), 'utf8');
    const copies = [...dockerfile.matchAll(/^COPY (?!.*--from=)(.+)$/gm)];
    assert(copies.length > 0);
    for (const [, args] of copies) {
      for (const source of args.trim().split(/\s+/).slice(0, -1)) {
        assert(existsSync(path.join(root, app.context, source)), `${app.id}: missing COPY ${source}`);
        assert(app.inputPaths.some(input => source === input.replace(/\/$/, '') || source.startsWith(input)), `${app.id}: unwatched COPY ${source}`);
      }
    }
  }
});

test('framework contexts match inspected upstream COPY and Maven reactor layouts', () => {
  for (const app of frameworkApps) {
    assert.equal(app.dockerfile, `examples/real-apps/${app.id}/Dockerfile`);
    assert.equal(app.context, frameworkRootApps.has(app.id) ? '.' : 'examples/real-apps');
  }
});

test('manual all selects twenty apps and source-specific immutable identities', async () => {
  const { apps, calls } = await select();
  assert.equal(apps.length, 20);
  for (const app of apps) {
    const sha = app.source === 'framework' ? frameworkSha : platformSha;
    assert.equal(app.sha, sha);
    assert(app.image.startsWith('ghcr.io/loom-ai-labs/'));
    assert(app.image.endsWith(`:${sha}-${app.id}`));
    assert.equal(app.repository, catalogue.sources[app.source].repository);
    assert(app.channel.endsWith(`:${app.id}-current`));
    assert.equal(app.promote, true);
  }
  for (const { args } of calls.filter(call => call.command === 'git' && call.args[0] === 'log')) {
    assert(args.includes('--'));
    assert(args.slice(args.indexOf('--') + 1).length > 0);
  }
});

test('manual selection accepts each exact catalogue ID and rejects unknown or injected IDs', async () => {
  for (const app of catalogue.apps) assert.deepEqual((await select({ app: app.id })).apps.map(row => row.id), [app.id]);
  await assert.rejects(select({ app: 'not-an-app' }), /Unknown catalogue app/);
  await assert.rejects(select({ app: '$(printenv)' }), /Unknown catalogue app/);
  await assert.rejects(select({ event: 'pull_request' }), /Unsupported event/);
});

test('scheduled checks repair coalesced pushes for both sources using their actual input SHAs', async () => {
  const { apps, calls } = await select({ event: 'schedule' });
  assert.equal(apps.length, 20);
  assert(apps.every(app => app.sha === (app.source === 'framework' ? frameworkSha : platformSha)));
  assert(calls.some(call => call.command === 'git' && call.options.cwd === 'framework-source'));
  assert(calls.some(call => call.command === 'git' && call.options.cwd === '.'));
});

test('existing images produce an empty matrix, including unchanged periodic checks', async () => {
  for (const event of ['schedule', 'workflow_dispatch', 'push']) {
    assert.deepEqual((await select({ event, exists: true, before: '0'.repeat(40) })).apps, []);
  }
});

test('missing/stale channels are repaired from existing immutable images without a required rebuild', async () => {
  const { apps } = await select({ app: 'partner-ui', exists: true, channelExists: false });
  assert.equal(apps.length, 1);
  assert.equal(apps[0].promote, true);
  assert.equal(apps[0].sha, platformSha);
});

test('explicit framework SHA requests never promote current channels, even if SHA happens to be current', async () => {
  const app = frameworkApps[0].id;
  const missing = await select({ app, frameworkRequest: frameworkSha });
  assert.equal(missing.apps[0].promote, false);
  assert.deepEqual((await select({ app, exists: true, channelExists: false, frameworkRequest: frameworkSha })).apps, []);
});

test('channel publication copies the immutable digest and rejects historical or stale branch snapshots', async () => {
  const { apps: [row] } = await select({ app: 'partner-ui' });
  async function promote(value, head = row.head) {
    const calls = [];
    await runPromotion(() => ({ execFileSync: (_command, args) => {
      calls.push(args);
      return args.includes('inspect') ? JSON.stringify({ digest: `sha256:${'a'.repeat(64)}` }) : '';
    } }), { env: { BUILD_ROW: JSON.stringify(value) } }, { info() {}, notice() {} }, {
      rest: { repos: { getBranch: async args => {
        assert.equal(args.branch, 'Platform-V11');
        return { data: { commit: { sha: head } } };
      } } },
    });
    return calls;
  }
  const calls = await promote(row);
  const create = calls.find(args => args.includes('create'));
  assert(create.includes('--prefer-index=false'));
  assert.equal(create.at(-2), row.channel);
  assert.equal(create.at(-1), `${row.image.split(':')[0]}@sha256:${'a'.repeat(64)}`);
  assert.deepEqual(await promote(row, 'f'.repeat(40)), []);
  await assert.rejects(promote({ ...row, promote: false }), /Historical source/);
});

test('registry checks are per-app and do not confuse other SHA/suffix identities', async () => {
  const { apps } = await select({ exists: image => !image.endsWith(`-${frameworkApps[0].id}`) });
  assert.deepEqual(apps.map(app => app.id), [frameworkApps[0].id]);
  await assert.rejects(select({ app: 'partner-ui', registryFails: true }), /GHCR lookup failed/);
});

test('push changes select only their affected local apps', async () => {
  for (const app of localApps) {
    const { apps } = await select({ event: 'push', changed: [`${app.inputPaths[0]}src/changed.txt`] });
    assert.deepEqual(apps.map(row => row.id), [app.id]);
  }
  assert.deepEqual((await select({ event: 'push', changed: ['Platfrom/backend/src/App.java', 'README.md'] })).apps, []);
  assert.deepEqual((await select({ event: 'push', changed: ['Platfrom/partner-ui-extra/file'] })).apps, []);
});

test('push rename/deletion inputs and shared Docker ignore changes are covered', async () => {
  const moved = await select({ event: 'push', changed: ['Platfrom/partner-ui/old.js', 'Platfrom/loomai-site/new.js'] });
  assert.deepEqual(moved.apps.map(app => app.id), ['loomai-site', 'partner-ui']);
  assert(moved.calls.some(call => call.args.includes('--no-renames') && call.args.includes('-z')));
  assert.equal((await select({ event: 'push', changed: ['.dockerignore'] })).apps.length, 5);
});

test('initial pushes, unavailable push bases, and recipe changes conservatively check only local apps', async () => {
  const cases = [
    { before: '0'.repeat(40) }, { before: '' }, { diffFails: true },
    { changed: ['.github/workflows/publish-serving-app-images.yml'] },
    { changed: ['scripts/infrastructure-monitoring/build-catalogue.json'] },
  ];
  for (const options of cases) {
    const { apps } = await select({ event: 'push', ...options });
    assert.equal(apps.length, 5);
    assert(apps.every(app => app.source === 'platform'));
  }
  await assert.rejects(select({ sourceSha: '' }), /No source commit/);
});

test('workflow keeps builds bounded, secret-free and off serving hosts', () => {
  assert.equal([...workflow.matchAll(/runs-on: ubuntu-latest/g)].length, 2);
  assert.match(workflow, /max-parallel: 2/);
  assert.match(workflow, /group: serving-app-images\n  cancel-in-progress: false/);
  assert.match(workflow, /cron: "7,22,37,52 \* \* \* \*"/);
  assert.match(workflow, /ref: \$\{\{ matrix.sha \}\}/);
  assert.match(workflow, /DOCKER_BUILD_RECORD_UPLOAD: "false"/);
  assert.match(workflow, /persist-credentials: false/);
  assert.match(workflow, /org.opencontainers.image.source=https:\/\/github.com\/\$\{\{ matrix.repository \}\}/);
  assert.match(workflow, /org.opencontainers.image.revision=\$\{\{ matrix.sha \}\}/);
  assert.match(workflow, /org.opencontainers.image.ref.name=\$\{\{ matrix.branch \}\}/);
  assert.deepEqual(new Set([...workflow.matchAll(/secrets\.([A-Z_]+)/g)].map(match => match[1])), new Set(['GITHUB_TOKEN']));
  assert(!/self-hosted|\bssh\b|upload-artifact|printenv|:latest\b|coolify\/deploy/i.test(workflow));
  for (const app of localApps) assert(workflow.includes(`"${app.inputPaths[0]}**"`));
});

test('upstream Dockerfiles and COPY context layouts at the inspected SHA', {
  skip: process.env.VERIFY_FRAMEWORK_SOURCE !== '1', timeout: 120_000,
}, async () => {
  const { repository, inspectedSha } = catalogue.sources.framework;
  for (const app of frameworkApps) {
    const base = `https://raw.githubusercontent.com/${repository}/${inspectedSha}`;
    const response = await fetch(`${base}/${app.dockerfile}`, { signal: AbortSignal.timeout(15_000) });
    assert.equal(response.status, 200, app.dockerfile);
    const dockerfile = await response.text();
    if (app.context === '.') {
      assert.match(dockerfile, /^COPY examples\/real-apps \.\/examples\/real-apps$/m);
      assert.match(dockerfile, /-f examples\/real-apps\/pom\.xml/);
    } else {
      assert.match(dockerfile, /^COPY \. \.$/m);
    }
    assert(dockerfile.includes(`-pl ${app.id} -am package`));
    assert(!dockerfile.includes('COPY ai-infrastructure-module'));
  }
});
