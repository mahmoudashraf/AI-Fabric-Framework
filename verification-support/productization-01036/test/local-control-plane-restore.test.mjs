import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {chmod, mkdtemp, open, readFile, rm, stat, symlink, writeFile} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import test from 'node:test';
import {assertLocalSocket, cleanupOwned, configuration, execute, safeCode,
  TABLES, validateContainer, validateCounts, validateImage, verifyControlPlaneRestore} from '../run-local-control-plane-restore.mjs';

const OPT_IN = {RUN_01036_LOCAL_CONTROL_PLANE_RESTORE: 'true'};
const HOST = 'unix:///private/tmp/control-plane-fixture.sock';
const LABEL = 'pro.loomai.local-control-plane-restore';
const IMAGE = `sha256:${'a'.repeat(64)}`;
const ID = 'b'.repeat(64);
const SECRET = 'PRIVATE_FIXTURE_ROW_NEVER_PRINT';
const argumentsFor = dump => ['--dump', dump, '--docker-host', HOST, '--postgres-major', '18', '--expected-migration', '191'];

async function privateFixture(t, contents = Buffer.from('PGDMP-fixture-only-not-a-real-archive')) {
  const directory = await mkdtemp('/private/tmp/01036-cp-restore-test-');
  const dump = resolve(directory, 'fixture.dump');
  await writeFile(dump, contents, {mode: 0o600});
  t.after(() => rm(directory, {recursive: true, force: true}));
  return dump;
}

function ownedContainer(fixture) {
  return {Id: ID, Name: `/${fixture.containerName}`, Image: IMAGE, State: {Paused: false, Status: 'created'},
    Config: {Labels: {[LABEL]: fixture.runId}, User: 'postgres', Entrypoint: ['docker-entrypoint.sh'], Cmd: ['postgres']},
    HostConfig: {NetworkMode: 'none', Privileged: false, ReadonlyRootfs: true, PortBindings: {}, Binds: [], Devices: [],
      VolumesFrom: [], PidMode: '', IpcMode: 'private', UTSMode: '', RestartPolicy: {Name: 'no'}, LogConfig: {Type: 'none'},
      CapDrop: ['ALL'], CapAdd: [], SecurityOpt: ['no-new-privileges'],
      Tmpfs: {'/tmp': 'rw,noexec,nosuid,nodev', '/var/run/postgresql': 'rw,noexec,nosuid,nodev'}},
    Mounts: [{Type: 'volume', Name: fixture.volumeName, Destination: fixture.volumePath}]};
}

function commandFixture(options = {}) {
  const calls = [];
  const fixture = {imageId: IMAGE, volumePath: '/var/lib/postgresql', containerId: ID};
  let containerExists = false, volumeExists = false;
  async function runCommand(program, original, runOptions) {
    assert.equal(program, 'docker');
    assert.deepEqual(original.slice(0, 2), ['--host', HOST]);
    const args = original.slice(2);
    calls.push({args, options: runOptions});
    const [command, subcommand] = args;
    if (command === 'image') {
      if (options.noImage) throw new Error(SECRET);
      return JSON.stringify([{Id: IMAGE, RepoDigests: [`postgres@sha256:${'c'.repeat(64)}`],
        Config: {Env: ['PG_MAJOR=18'], Entrypoint: ['docker-entrypoint.sh'], Cmd: ['postgres'], Volumes: {'/var/lib/postgresql': {}}}}]);
    }
    if (command === 'volume' && subcommand === 'create') {
      fixture.volumeName = args.at(-1); fixture.runId = args[args.indexOf('--label') + 1].split('=')[1];
      volumeExists = true; return fixture.volumeName;
    }
    if (command === 'volume' && subcommand === 'inspect') return JSON.stringify([{Name: fixture.volumeName,
      Labels: {[LABEL]: options.foreignVolume ? 'foreign' : fixture.runId}, Driver: 'local', Options: {}}]);
    if (command === 'container' && subcommand === 'create') {
      fixture.containerName = args[args.indexOf('--name') + 1];
      containerExists = true;
      if (options.partialCreateFailure) throw new Error(SECRET);
      return ID;
    }
    if (command === 'container' && subcommand === 'inspect') {
      const item = ownedContainer(fixture);
      if (options.unsafeContainer) item.HostConfig.NetworkMode = 'bridge';
      return JSON.stringify([item]);
    }
    if (command === 'container' && subcommand === 'start') return '';
    if (command === 'container' && subcommand === 'rm') { containerExists = false; return ''; }
    if (command === 'volume' && subcommand === 'rm') { volumeExists = false; return ''; }
    if (subcommand === 'ls') {
      assert.ok(args.includes(`label=${LABEL}=${fixture.runId}`));
      return command === 'container' ? containerExists ? ID : '' : volumeExists ? fixture.volumeName : '';
    }
    if (command === 'exec' && args.includes('pg_isready')) return '';
    if (command === 'exec' && args.includes('/proc/1/comm')) return 'postgres';
    if (command === 'exec' && args.includes('pg_restore')) {
      assert.ok(runOptions.input);
      if (args.includes('--list')) return options.catalog ?? '; Format: CUSTOM\n; Dumped from database version: 18.6\n; Dumped by pg_dump version: 18.6\n';
      if (options.restoreFailure) throw new Error(`COPY failed: ${SECRET}`);
      return '';
    }
    if (command === 'exec' && args.includes('psql')) {
      const sql = args.at(-1);
      if (sql.includes("'major'")) return JSON.stringify({major: 18, empty: !options.nonempty});
      if (sql.includes("'publicSchema'")) {
        const keys = [...sql.matchAll(/'([a-z_]+\.[a-z_]+)', EXISTS/g)].map(match => match[1]);
        const columns = Object.fromEntries(keys.map(key => [key, true]));
        if (options.wrongType) columns['platform_users.password_hash'] = false;
        return JSON.stringify({publicSchema: true, tableCount: 120, columns});
      }
      if (sql.includes("'migrationRows'")) return JSON.stringify(countsFixture(options.badMigration));
    }
    throw new Error('UNEXPECTED_FIXTURE_COMMAND');
  }
  return {runCommand, calls, fixture};
}

function countsFixture(badMigration = false) {
  return {counts: Object.fromEntries(TABLES.map(table => [table, table === 'flyway_schema_history' ? '191' : '1'])),
    migrationRows: '191', migrationChainMatches: !badMigration, unvalidatedConstraints: '0', invalidIndexes: '0'};
}

async function verifyFixture(t, options = {}, transform = async dump => dump) {
  const dump = await transform(await privateFixture(t));
  const fake = commandFixture(options);
  const result = await verifyControlPlaneRestore(configuration(argumentsFor(dump), OPT_IN), {
    runCommand: fake.runCommand, socketProbe: async host => assert.equal(host, HOST),
  });
  t.after(() => rm(dirname(result.reportPath), {recursive: true, force: true}));
  assert.ok(!JSON.stringify(result).includes(SECRET));
  return {...result, ...fake};
}

test('requires explicit opt-in and rejects remote/destructive/extra input', async () => {
  const args = argumentsFor('/private/tmp/archive.dump');
  assert.throws(() => configuration(args, {}), /EXPLICIT_LOCAL_RESTORE_OPT_IN_REQUIRED/);
  for (const host of ['tcp://localhost:2375', 'tcp://production.example:2376', 'ssh://operator@host',
    'http://127.0.0.1', 'unix://relative.sock', 'unix:///private/tmp/../tmp/docker.sock', 'unix:///tmp/sock?x=1']) {
    const changed = [...args]; changed[3] = host;
    assert.throws(() => configuration(changed, OPT_IN), /EXPLICIT_LOCAL_UNIX_DOCKER_REQUIRED/);
    await assert.rejects(assertLocalSocket(host), /EXPLICIT_LOCAL_UNIX_DOCKER_REQUIRED/);
  }
  for (const extra of [['--clean', 'true'], ['--db-url', 'postgres://production'], ['--container', 'existing'],
    ['--keep', 'true'], ['--output', '/tmp/public'], ['--dump', '/private/tmp/other.dump'], ['--image']]) {
    assert.throws(() => configuration([...args, ...extra], OPT_IN), /EXACT_ARGUMENTS_REQUIRED/);
  }
  for (const dump of ['relative.dump', 'https://private/archive', '/private/tmp/../archive.dump', '/private/tmp/file\n.dump']) {
    const changed = [...args]; changed[1] = dump;
    assert.throws(() => configuration(changed, OPT_IN), /ABSOLUTE_PRIVATE_DUMP_REQUIRED/);
  }
  for (const image of ['postgres:16-alpine', 'postgres:latest', 'attacker/postgres:18', 'https://registry/postgres', 'postgres']) {
    assert.throws(() => configuration([...args, '--image', image], OPT_IN), /OFFICIAL_MATCHING_MAJOR_IMAGE_REQUIRED/);
  }
  for (const migration of ['0', '191; DROP DATABASE postgres', '-1', '191.1']) {
    const changed = [...args]; changed[7] = migration;
    assert.throws(() => configuration(changed, OPT_IN), /EXACT_MIGRATION_CEILING_REQUIRED/);
  }
});

test('official catalog source/tool major and format must match before data restore', async t => {
  for (const catalog of [
    '; Format: CUSTOM\n; Dumped from database version: 16.13\n; Dumped by pg_dump version: 18.6\n',
    '; Format: CUSTOM\n; Dumped from database version: 18.6\n; Dumped by pg_dump version: 19.1\n',
    '; Format: TAR\n; Dumped from database version: 18.6\n; Dumped by pg_dump version: 18.6\n', SECRET,
  ]) {
    const {report, calls} = await verifyFixture(t, {catalog});
    assert.equal(report.code, 'ARCHIVE_AND_RESTORE_MAJOR_MUST_MATCH');
    assert.equal(report.actualRestoreCompleted, false); assert.equal(report.cleanup, 'OWNED_FIXTURES_REMOVED');
    assert.ok(!calls.some(call => call.args.includes('pg_restore') && !call.args.includes('--list')));
  }
});

test('fixture-only full workflow verifies isolation, restore flags, typed aggregates, private report and cleanup', async t => {
  const {report, reportPath, calls, fixture} = await verifyFixture(t);
  assert.equal(report.status, 'PASS'); assert.equal(report.actualRestoreCompleted, true);
  assert.equal(report.cleanup, 'OWNED_FIXTURES_REMOVED'); assert.equal(report.archive.sourceMajor, 18);
  assert.equal(report.schema.publicTables, 120); assert.equal(report.integrity.migrationRows, '191');
  assert.equal((await stat(reportPath)).mode & 0o777, 0o600);
  assert.equal((await stat(dirname(reportPath))).mode & 0o777, 0o700);
  assert.deepEqual(JSON.parse(await readFile(reportPath, 'utf8')), report);
  const create = calls.find(call => call.args[0] === 'container' && call.args[1] === 'create').args;
  for (const flag of ['--pull=never', '--network=none', '--read-only', '--user=postgres', '--cap-drop=ALL', '--log-driver=none']) assert.ok(create.includes(flag));
  assert.ok(!calls.some(call => call.args.some(arg => ['pull', 'prune', '--publish', '-p', 'unpause', 'logs'].includes(arg))));
  const restore = calls.find(call => call.args.includes('pg_restore') && !call.args.includes('--list')).args;
  for (const flag of ['--single-transaction', '--exit-on-error', '--no-owner', '--no-privileges', '--no-subscriptions']) assert.ok(restore.includes(flag));
  assert.ok(!restore.some(arg => ['--clean', '--create', '--verbose'].includes(arg)));
  assert.match(fixture.containerName, /^cp-restore-[a-f0-9-]{36}$/);
  assert.match(fixture.volumeName, /^cp-restore-data-[a-f0-9-]{36}$/);
  for (const {args} of calls.filter(call => call.args[1] === 'rm')) assert.ok([ID, fixture.volumeName].includes(args.at(-1)));
});

for (const [name, options, code, restored] of [
  ['missing cached major', {noImage: true}, 'CACHED_MATCHING_MAJOR_IMAGE_UNAVAILABLE_NO_PULL', false],
  ['partially successful create', {partialCreateFailure: true}, 'LOCAL_RESTORE_UNAVAILABLE', false],
  ['nonempty target', {nonempty: true}, 'FRESH_MATCHING_MAJOR_DATABASE_REQUIRED', false],
  ['secret-bearing pg_restore failure', {restoreFailure: true}, 'PG_RESTORE_FAILED_DIAGNOSTICS_REDACTED', false],
  ['wrong column type', {wrongType: true}, 'TYPED_CONTROL_PLANE_SCHEMA_REQUIRED', true],
  ['wrong migration chain', {badMigration: true}, 'MIGRATION_COUNTS_OR_INTEGRITY_CHECK_FAILED', true],
]) {
  test(`fails closed on ${name} and removes only its fixtures`, async t => {
    const {report, calls} = await verifyFixture(t, options);
    assert.equal(report.status, 'FAIL'); assert.equal(report.code, code); assert.equal(report.actualRestoreCompleted, restored);
    assert.equal(report.cleanup, options.noImage ? 'NOT_CREATED' : 'OWNED_FIXTURES_REMOVED');
    if (options.noImage) assert.equal(calls.length, 1);
  });
}

test('unsafe ownership or network refuses startup/deletion and reports cleanup unproven', async t => {
  for (const options of [{foreignVolume: true}, {unsafeContainer: true}]) {
    const {report, calls} = await verifyFixture(t, options);
    assert.equal(report.status, 'FAIL'); assert.equal(report.cleanup, 'NOT_PROVEN');
    assert.ok(!calls.some(call => call.args[1] === 'start' || call.args[1] === 'rm'));
  }
});

test('rejects loose permissions, symlinks and non-custom archives before Docker access', async t => {
  for (const transform of [async dump => { await chmod(dump, 0o644); return dump; },
    async dump => { const alias = resolve(dirname(dump), 'alias.dump'); await symlink(dump, alias); return alias; },
    async dump => { await writeFile(dump, 'plain SQL fixture'); return dump; },
    async dump => { await chmod(dirname(dump), 0o755); return dump; }]) {
    const {report, calls} = await verifyFixture(t, {}, transform);
    assert.equal(report.status, 'FAIL'); assert.equal(report.cleanup, 'NOT_CREATED'); assert.equal(calls.length, 0);
  }
});

test('cleanup rejects foreign identities, paused fixtures, extra resources and unsafe mounts', async () => {
  const fixture = {runId: 'generated-run', containerName: 'cp-restore-generated-run', volumeName: 'cp-restore-data-generated-run',
    containerId: ID, imageId: IMAGE, volumePath: '/var/lib/postgresql'};
  for (const mutate of [item => {item.Config.Labels[LABEL] = 'foreign';}, item => {item.Name = '/paused-service';},
    item => {item.State.Paused = true;}, item => {item.Image = `sha256:${'e'.repeat(64)}`;},
    item => {item.HostConfig.Binds = ['/var/run/docker.sock:/socket'];}, item => {item.HostConfig.PortBindings = {'5432/tcp': [{HostPort: '5432'}]};},
    item => {item.Mounts[0].Name = 'existing-data';}, item => {item.HostConfig.NetworkMode = 'host';}]) {
    const item = ownedContainer(fixture); mutate(item);
    assert.throws(() => validateContainer(item, fixture), /OWNED_CONTAINER_/);
    const calls = [];
    await assert.rejects(cleanupOwned(async args => {
      calls.push(args);
      if (args[1] === 'ls') return ID;
      if (args[1] === 'inspect') return JSON.stringify([item]);
      throw new Error('UNEXPECTED_DELETE');
    }, fixture), /OWNED_CONTAINER_/);
    assert.ok(!calls.some(args => args[1] === 'rm'));
  }
  const calls = [];
  await assert.rejects(cleanupOwned(async args => {calls.push(args); return `${ID}\n${'f'.repeat(64)}`;}, fixture), /CLEANUP_SCOPE_NOT_PROVEN/);
  assert.equal(calls.length, 1);
});

test('typed count checks reject injected content, negative counts, missing fields and integrity drift', () => {
  for (const mutate of [value => {value.counts.platform_secrets = SECRET;}, value => {value.counts.platform_users = '-1';},
    value => {delete value.counts.platform_deployments;}, value => {value.unvalidatedConstraints = '1';},
    value => {value.invalidIndexes = '1';}, value => {value.counts.platform_customers = '0';},
    value => {value.content = SECRET;}]) {
    const value = countsFixture(); mutate(value);
    assert.throws(() => validateCounts(value, 191), /CHECK_FAILED|ROWS_REQUIRED/);
  }
});

test('subprocess stderr, timeout errors and arbitrary Error messages never escape', async () => {
  await assert.rejects(execute(process.execPath, ['-e', `process.stderr.write(${JSON.stringify(SECRET)}); process.exit(9);`]), error => {
    assert.equal(safeCode(error), 'LOCAL_COMMAND_FAILED'); assert.ok(!error.message.includes(SECRET)); return true;
  });
  await assert.rejects(execute(process.execPath, ['-e', 'setTimeout(() => {}, 10000)'], {timeout: 30}), /LOCAL_COMMAND_TIMEOUT/);
  assert.equal(safeCode(new Error(SECRET)), 'LOCAL_RESTORE_UNAVAILABLE');
  assert.equal(safeCode(new Error('UPPERCASE_SECRET_VALUE')), 'LOCAL_RESTORE_UNAVAILABLE');
});

test('archive stdin can be reused without leaking or truncating its bytes', async t => {
  const dump = await privateFixture(t);
  const input = await open(dump, 'r');
  try {
    for (let pass = 0; pass < 3; pass++) {
      const result = await execute(process.execPath, ['-e', "let n=0;process.stdin.on('data',b=>n+=b.length);process.stdin.on('end',()=>process.stdout.write(String(n)));"], {input, capture: true});
      assert.equal(result, String((await stat(dump)).size));
    }
  } finally { await input.close(); }
});

test('opt-in real Docker: synthetic PG16 archive restores and cleans up; not private PG18 acceptance', {
  skip: process.env.RUN_01036_LOCAL_CONTROL_PLANE_DOCKER_TEST !== 'true', timeout: 120000,
}, async t => {
  const host = process.env.CP_RESTORE_TEST_DOCKER_HOST;
  const dump = await privateFixture(t);
  const config = configuration(['--dump', dump, '--docker-host', host, '--postgres-major', '16', '--expected-migration', '1'], OPT_IN);
  await assertLocalSocket(host);
  const docker = async (args, options) => {
    const phase = args[0] === 'exec' ? ['pg_isready', 'psql', 'pg_dump', 'cat'].find(program => args.includes(program)) : args[1];
    try { return await execute('docker', ['--host', host, ...args], options); }
    catch (error) { throw new Error(`SYNTHETIC_${args[0].toUpperCase()}_${phase?.toUpperCase()}_${safeCode(error)}`); }
  };
  const image = validateImage(JSON.parse(await docker(['image', 'inspect', config.image], {capture: true})), config);
  const runId = randomUUID();
  const fixture = {...image, runId, containerName: `cp-restore-${runId}`, volumeName: `cp-restore-data-${runId}`};
  try {
    await docker(['volume', 'create', '--driver', 'local', '--label', `${LABEL}=${runId}`, fixture.volumeName]);
    const [volume] = JSON.parse(await docker(['volume', 'inspect', fixture.volumeName], {capture: true}));
    assert.equal(volume.Name, fixture.volumeName); assert.equal(volume.Labels[LABEL], runId);
    assert.equal(volume.Driver, 'local'); assert.deepEqual(volume.Options ?? {}, {});
    fixture.containerId = await docker(['container', 'create', '--pull=never', '--name', fixture.containerName,
      '--label', `${LABEL}=${runId}`, '--network=none', '--restart=no', '--no-healthcheck', '--log-driver=none',
      '--read-only', '--user=postgres', '--cap-drop=ALL', '--security-opt=no-new-privileges',
      '--pids-limit=128', '--memory=768m', '--cpus=1',
      '--tmpfs', '/tmp:rw,noexec,nosuid,nodev,size=64m,mode=1777',
      '--tmpfs', '/var/run/postgresql:rw,noexec,nosuid,nodev,size=16m,mode=1777',
      '--mount', `type=volume,source=${fixture.volumeName},target=${fixture.volumePath}`,
      '--env', 'POSTGRES_DB=control_plane_restore', '--env', 'POSTGRES_USER=restore_verifier',
      '--env', `POSTGRES_PASSWORD=${randomUUID()}`, '--env', 'POSTGRES_INITDB_ARGS=--auth-local=trust --auth-host=reject',
      '--env', `PGDATA=${fixture.volumePath}/01036-data`, '--entrypoint=docker-entrypoint.sh', fixture.imageId,
      'postgres', '-c', 'listen_addresses=', '-c', 'log_min_messages=panic', '-c', 'log_min_error_statement=panic',
      '-c', 'shared_preload_libraries=', '-c', 'max_worker_processes=0'], {capture: true});
    const [item] = JSON.parse(await docker(['container', 'inspect', fixture.containerId], {capture: true}));
    validateContainer(item, fixture);
    await docker(['container', 'start', fixture.containerId]);
    let ready = false;
    for (let attempt = 0; attempt < 40 && !ready; attempt++) {
      try {
        if (await docker(['exec', fixture.containerId, 'cat', '/proc/1/comm'], {capture: true}) === 'postgres') {
          await docker(['exec', fixture.containerId, 'pg_isready', '--host', '/var/run/postgresql',
            '--username', 'restore_verifier', '--dbname', 'control_plane_restore']); ready = true;
        } else await new Promise(accept => setTimeout(accept, 500));
      }
      catch { await new Promise(accept => setTimeout(accept, 500)); }
    }
    assert.ok(ready, 'SYNTHETIC_POSTGRES_READY_REQUIRED');
    const fields = {
      flyway_schema_history: 'installed_rank int, version varchar, success boolean, type varchar',
      platform_customers: 'id varchar, status varchar', platform_customer_tenants: 'id varchar, customer_id varchar',
      platform_deployments: 'id varchar, customer_id varchar, tenant_id varchar',
      platform_deployment_drafts: 'id varchar, deployment_id varchar, provider_config_json text',
      platform_deployment_versions: 'id varchar, deployment_id varchar, manifest_json text',
      platform_deployment_releases: 'id varchar, deployment_id varchar, provisioning_details_json text',
      platform_users: 'id varchar, password_hash varchar', platform_user_sessions: 'id varchar, token_hash varchar',
      platform_secrets: 'name varchar, secret_value text', deployment_target_profiles: 'id varchar, provider_config_json text',
      deployment_provider_credentials: 'id varchar, secret_ref varchar',
      deployment_capacity_reservations: 'id varchar, revision bigint, current_slot varchar',
      platform_tenant_scoped_vector_resources: 'id varchar, target_profile_id varchar, details_json text',
      ai_workspace_installations: 'id varchar, connection_configuration_json text',
    };
    const sql = Object.entries(fields).map(([table, columns]) => `CREATE TABLE public.${table} (${columns});`).join('\n')
      + "INSERT INTO public.flyway_schema_history VALUES (1,'1',true,'SQL');"
      + "INSERT INTO public.platform_customers VALUES ('synthetic','ACTIVE');"
      + "INSERT INTO public.platform_users VALUES ('synthetic','not-a-real-password-hash');"
      + "INSERT INTO public.deployment_target_profiles VALUES ('synthetic','{}');"
      + "INSERT INTO public.platform_secrets VALUES ('synthetic','not-a-real-credential');";
    await docker(['exec', fixture.containerId, 'psql', '-X', '--host', '/var/run/postgresql', '--username', 'restore_verifier',
      '--dbname', 'control_plane_restore', '--set', 'ON_ERROR_STOP=1', '--command', sql]);
    const output = await open(dump, 'w', 0o600);
    try {
      await new Promise((accept, reject) => {
        const child = spawn('docker', ['--host', host, 'exec', fixture.containerId, 'pg_dump', '--host', '/var/run/postgresql',
          '--username', 'restore_verifier', '--dbname', 'control_plane_restore', '--format=custom'],
        {env: {PATH: process.env.PATH, HOME: process.env.HOME}, stdio: ['ignore', output.fd, 'ignore']});
        const timer = setTimeout(() => child.kill('SIGKILL'), 30000);
        child.on('error', () => { clearTimeout(timer); reject(new Error('SYNTHETIC_ARCHIVE_TOOL_UNAVAILABLE')); });
        child.on('close', code => { clearTimeout(timer); code === 0 ? accept() : reject(new Error('SYNTHETIC_ARCHIVE_WRITE_FAILED')); });
      });
    } finally { await output.close(); }
  } finally { await cleanupOwned(docker, fixture); }
  const result = await verifyControlPlaneRestore(config);
  t.after(() => rm(dirname(result.reportPath), {recursive: true, force: true}));
  assert.equal(result.report.status, 'PASS', `SYNTHETIC_RESTORE_FAILED_${result.report.code ?? result.report.cleanup}`);
  assert.equal(result.report.actualRestoreCompleted, true);
  assert.equal(result.report.cleanup, 'OWNED_FIXTURES_REMOVED');
  assert.equal(result.report.archive.sourceMajor, 16);
  assert.equal(result.report.schema.publicTables, TABLES.length);
  assert.equal(result.report.integrity.migrationRows, '1');
});
