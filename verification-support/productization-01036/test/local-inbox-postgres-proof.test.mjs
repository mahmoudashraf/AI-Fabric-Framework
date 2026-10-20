import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {configuration, IMAGE, LABEL, localUrl, owned, SOURCE, TABLES, validateInboxImage, validatePostgresImage, validateIsolation}
  from '../run-local-inbox-postgres-proof.mjs';

const env = {RUN_01036_LOCAL_INBOX_POSTGRES_PROOF: 'true'};
const args = ['--docker-host', 'unix:///var/run/docker.sock', '--output-root', '/private/tmp/inbox-proof-test'];
test('explicit opt-in and new private root required', () => {
  assert.throws(() => configuration(args, {}), /OPT_IN/);
  assert.deepEqual(configuration(args, env), {host: 'unix:///var/run/docker.sock', root: '/private/tmp/inbox-proof-test', pull: false});
});
for (const host of ['tcp://127.0.0.1:2375', 'ssh://localhost', 'unix:///tmp/../docker.sock', 'unix:///tmp/a\n']) {
  test(`reject nonlocal or noncanonical daemon ${JSON.stringify(host)}`, () => assert.throws(() => configuration(['--docker-host', host, ...args.slice(2)], env)));
}
for (const root of ['/tmp/proof', '/private/tmp/a/child', '/private/tmp/../proof', '/Users/test', '/private/tmp/a\n']) {
  test(`reject unsafe root ${JSON.stringify(root)}`, () => assert.throws(() => configuration([...args.slice(0, 2), '--output-root', root], env)));
}
test('reject unknown, duplicate, missing and nonboolean CLI options', () => {
  for (const tail of [['--image', 'other'], ['--docker-host', 'unix:///var/run/docker.sock'], ['--unknown'], ['--allow-anonymous-exact-pull', 'yes']]) {
    assert.throws(() => configuration([...args, ...tail], env));
  }
  assert.equal(configuration([...args, '--allow-anonymous-exact-pull', 'true'], env).pull, true);
});
const image = () => ({Id: `sha256:${'a'.repeat(64)}`, RepoDigests: [IMAGE], Os: 'linux', Architecture: 'amd64', Config: {Labels: {
  'org.opencontainers.image.revision': SOURCE,
  'org.opencontainers.image.source': 'https://github.com/mahmoudashraf/AI-Fabric-Framework'}}});
test('only exact digest plus exact source lineage accepted', () => {
  assert.equal(validateInboxImage(image()).digest, IMAGE);
  for (const mutate of [i => i.RepoDigests = ['other'], i => i.Config.Labels['org.opencontainers.image.revision'] = 'other',
    i => i.Architecture = 'arm64', i => i.Config.OnBuild = ['RUN bad']]) {
    const value = image(); mutate(value); assert.throws(() => validateInboxImage(value));
  }
});
test('PostgreSQL must be cached official major16', () => {
  const pg = {Id: `sha256:${'a'.repeat(64)}`, RepoDigests: [`postgres@sha256:${'b'.repeat(64)}`],
    Config: {Env: ['PG_MAJOR=16'], Entrypoint: ['docker-entrypoint.sh']}};
  assert.equal(validatePostgresImage(pg).id, pg.Id);
  assert.throws(() => validatePostgresImage({...pg, Config: {...pg.Config, Env: ['PG_MAJOR=15']}}));
  assert.throws(() => validatePostgresImage({...pg, RepoDigests: []}));
});
test('cleanup requires exact run label and resource identity', () => {
  for (const type of ['container', 'network', 'volume']) {
    const row = {Id: 'id', Name: 'id', Labels: {[LABEL]: 'run'}, Config: {Labels: {[LABEL]: 'run'}}};
    owned(row, type, 'id', 'run');
    assert.throws(() => owned(row, type, 'other-id', 'run'));
    assert.throws(() => owned(row, type, 'id', 'other-run'));
  }
});
test('HTTP rejects external destinations, credentials, HTTPS and hostname aliases', () => {
  assert.equal(localUrl('http://127.0.0.1:1234/x').hostname, '127.0.0.1');
  for (const value of ['http://example.com', 'http://localhost:80', 'https://127.0.0.1', 'http://a:b@127.0.0.1']) assert.throws(() => localUrl(value));
});
test('actual Docker isolation rejects broad networking, egress DNS, ports and unbounded resources', () => {
  const row = () => ({Id: 'id', Image: 'image', Config: {User: '10001', Labels: {[LABEL]: 'run'}}, Mounts: [],
    HostConfig: {NetworkMode: 'net', Privileged: false, ReadonlyRootfs: true, NanoCpus: 1000000000,
      Memory: 256 * 1024 ** 2, PidsLimit: 160, CapDrop: ['ALL'], SecurityOpt: ['no-new-privileges'],
      PortBindings: {}, RestartPolicy: {Name: 'no'}, Dns: ['127.0.0.1']}, NetworkSettings: {Networks: {net: {}}}});
  const fixture = {network: 'net', imageId: 'image', id: 'id', runId: 'run'};
  validateIsolation(row(), fixture);
  for (const mutate of [r => r.HostConfig.NetworkMode = 'host', r => r.HostConfig.Privileged = true,
    r => r.HostConfig.PortBindings = {'8085/tcp': [{HostIp: '0.0.0.0'}]}, r => r.HostConfig.Dns = ['8.8.8.8'],
    r => r.HostConfig.Memory = 0, r => r.HostConfig.NanoCpus = 0, r => r.HostConfig.CapAdd = ['SYS_ADMIN'],
    r => r.NetworkSettings.Networks.other = {}]) {
    const value = row(); mutate(value); assert.throws(() => validateIsolation(value, fixture));
  }
});
test('all actual Inbox tables included, no backend scope', () => {
  assert.equal(TABLES.length, 8);
  assert.equal(TABLES.includes('inbox_dispatch_receipt'), true);
  assert.equal(TABLES.includes('inbox_operator_operation'), true);
  assert.equal(TABLES.some(name => name.startsWith('platform_')), false);
});
test('no build, global prune, arbitrary pull or shared Maven target in runner', async () => {
  const source = await readFile(new URL('../run-local-inbox-postgres-proof.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /\b(mvn|prune|docker\.sock:)\b/);
  assert.match(source, /docker\('pull', '--platform', 'linux\/amd64', IMAGE\)/);
  assert.match(source, /'--internal'/);
  assert.doesNotMatch(source, /'--publish'|'--publish-all'|'127\.0\.0\.1::/);
  assert.match(source, /OWNED_EXEC_TRANSPORT_REQUIRED/);
  assert.match(source, /AI_ENABLED: 'false'/);
  assert.match(source, /'--pull', 'never'/);
});
