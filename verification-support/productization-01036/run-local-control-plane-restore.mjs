import {createHash, randomUUID} from 'node:crypto';
import {spawn} from 'node:child_process';
import {constants} from 'node:fs';
import {chmod, lstat, mkdtemp, open, realpath, stat, writeFile} from 'node:fs/promises';
import {dirname, isAbsolute, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

// Never log command arguments, diagnostics, catalog text, SQL, or database values.
const LABEL = 'pro.loomai.local-control-plane-restore';
const DATABASE = 'control_plane_restore';
const USER = 'restore_verifier';
const SOCKET = '/var/run/postgresql';
const SHA = /^sha256:[a-f0-9]{64}$/;
const COUNT = /^(0|[1-9][0-9]{0,18})$/;
const COLUMNS = {
  flyway_schema_history: {installed_rank: 'int4', version: 'varchar', success: 'bool'},
  platform_customers: {id: 'varchar', status: 'varchar'},
  platform_customer_tenants: {id: 'varchar', customer_id: 'varchar'},
  platform_deployments: {id: 'varchar', customer_id: 'varchar', tenant_id: 'varchar'},
  platform_deployment_drafts: {id: 'varchar', deployment_id: 'varchar', provider_config_json: 'text'},
  platform_deployment_versions: {id: 'varchar', deployment_id: 'varchar', manifest_json: 'text'},
  platform_deployment_releases: {id: 'varchar', deployment_id: 'varchar', provisioning_details_json: 'text'},
  platform_users: {id: 'varchar', password_hash: 'varchar'},
  platform_user_sessions: {id: 'varchar', token_hash: 'varchar'},
  platform_secrets: {name: 'varchar', secret_value: 'text'},
  deployment_target_profiles: {id: 'varchar', provider_config_json: 'text'},
  deployment_provider_credentials: {id: 'varchar', secret_ref: 'varchar'},
  deployment_capacity_reservations: {id: 'varchar', revision: 'int8', current_slot: 'varchar'},
  platform_tenant_scoped_vector_resources: {id: 'varchar', target_profile_id: 'varchar', details_json: 'text'},
  ai_workspace_installations: {id: 'varchar', connection_configuration_json: 'text'},
};
export const TABLES = Object.freeze(Object.keys(COLUMNS));
const COLUMN_KEYS = Object.entries(COLUMNS).flatMap(([table, columns]) => Object.keys(columns).map(column => `${table}.${column}`));

class RestoreFailure extends Error {}
function check(condition, code) { if (!condition) throw new RestoreFailure(code); }
export function safeCode(error) { return error instanceof RestoreFailure ? error.message : 'LOCAL_RESTORE_UNAVAILABLE'; }
function json(value) {
  try { return JSON.parse(value); } catch { throw new RestoreFailure('TYPED_OUTPUT_REQUIRED'); }
}
function exactKeys(value, keys) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    && Object.keys(value).length === keys.length && keys.every(key => Object.hasOwn(value, key));
}

export function configuration(args, env = process.env) {
  check(env.RUN_01036_LOCAL_CONTROL_PLANE_RESTORE === 'true', 'EXPLICIT_LOCAL_RESTORE_OPT_IN_REQUIRED');
  const allowed = new Set(['--dump', '--docker-host', '--postgres-major', '--expected-migration', '--image']);
  const values = {};
  for (let index = 0; index < args.length; index += 2) {
    const key = args[index];
    check(allowed.has(key) && !Object.hasOwn(values, key) && typeof args[index + 1] === 'string', 'EXACT_ARGUMENTS_REQUIRED');
    values[key] = args[index + 1];
  }
  const dump = values['--dump'];
  check(typeof dump === 'string' && isAbsolute(dump) && resolve(dump) === dump && !/[\x00-\x1f\x7f]/.test(dump), 'ABSOLUTE_PRIVATE_DUMP_REQUIRED');
  const dockerHost = values['--docker-host'];
  check(typeof dockerHost === 'string' && /^unix:\/\/\/[a-zA-Z0-9_./-]+$/.test(dockerHost)
    && resolve(dockerHost.slice(7)) === dockerHost.slice(7), 'EXPLICIT_LOCAL_UNIX_DOCKER_REQUIRED');
  const major = values['--postgres-major'];
  const migration = values['--expected-migration'];
  check(/^(1[6-9]|[2-9][0-9])$/.test(major ?? ''), 'SUPPORTED_POSTGRES_MAJOR_REQUIRED');
  check(/^[1-9][0-9]{0,5}$/.test(migration ?? ''), 'EXACT_MIGRATION_CEILING_REQUIRED');
  const image = values['--image'] ?? `postgres:${major}-alpine`;
  check(new RegExp(`^postgres(?::${major}(?:\\.[0-9]+)?(?:-alpine(?:[0-9.]+)?)?)?(?:@sha256:[a-f0-9]{64})?$`).test(image)
    && image !== 'postgres', 'OFFICIAL_MATCHING_MAJOR_IMAGE_REQUIRED');
  return Object.freeze({dump, dockerHost, major: Number(major), migration: Number(migration), image});
}

export async function assertLocalSocket(host) {
  check(typeof host === 'string' && /^unix:\/\/\/[a-zA-Z0-9_./-]+$/.test(host)
    && resolve(host.slice(7)) === host.slice(7), 'EXPLICIT_LOCAL_UNIX_DOCKER_REQUIRED');
  const socket = await stat(host.slice(7));
  check(socket.isSocket() && (socket.uid === process.getuid() || socket.uid === 0), 'LOCAL_DOCKER_SOCKET_REQUIRED');
}

async function privateDump(path) {
  check(await realpath(path) === path, 'SYMLINK_DUMP_FORBIDDEN');
  const parent = await lstat(dirname(path));
  check(parent.isDirectory() && parent.uid === process.getuid() && (parent.mode & 0o077) === 0, 'PRIVATE_DUMP_DIRECTORY_REQUIRED');
  const file = await open(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const info = await file.stat();
    check(info.isFile() && info.uid === process.getuid() && info.nlink === 1 && (info.mode & 0o077) === 0
      && info.size > 5 && info.size <= 20 * 1024 ** 3, 'PRIVATE_REGULAR_DUMP_REQUIRED');
    const magic = Buffer.alloc(5);
    await file.read(magic, 0, 5, 0);
    check(magic.equals(Buffer.from('PGDMP')), 'CUSTOM_POSTGRES_ARCHIVE_REQUIRED');
    return {file, bytes: info.size};
  } catch (error) { await file.close(); throw error; }
}

async function archiveHash(file) {
  const hash = createHash('sha256');
  const buffer = Buffer.alloc(64 * 1024);
  let position = 0;
  for (;;) {
    const {bytesRead} = await file.read(buffer, 0, buffer.length, position);
    if (!bytesRead) break;
    hash.update(buffer.subarray(0, bytesRead)); position += bytesRead;
  }
  return hash.digest('hex');
}

export async function execute(program, args, {capture = false, input, timeout = 15000, signal} = {}) {
  // Do not inherit PG*, DOCKER_*, provider credentials, preload hooks, or proxy settings.
  const env = Object.fromEntries(['PATH', 'HOME', 'LANG', 'LC_ALL', 'TMPDIR']
    .filter(key => process.env[key] !== undefined).map(key => [key, process.env[key]]));
  return new Promise((accept, reject) => {
    const child = spawn(program, args, {env, stdio: [input ? 'pipe' : 'ignore', 'pipe', 'pipe']});
    let output = '', size = 0, failure;
    const stop = code => { failure ??= code; child.kill('SIGKILL'); };
    const abort = () => stop('LOCAL_RESTORE_CANCELLED');
    signal?.addEventListener('abort', abort, {once: true});
    if (signal?.aborted) abort();
    const timer = setTimeout(() => stop('LOCAL_COMMAND_TIMEOUT'), timeout);
    child.stdout.on('data', chunk => {
      size += chunk.length;
      if (size > 2 * 1024 * 1024) stop('LOCAL_OUTPUT_LIMIT');
      else if (capture) output += chunk.toString('utf8');
    });
    // pg_restore can put raw COPY rows in stderr. Discard, do not persist or echo it.
    child.stderr.on('data', chunk => { size += chunk.length; if (size > 2 * 1024 * 1024) stop('LOCAL_OUTPUT_LIMIT'); });
    child.on('error', () => { failure = 'LOCAL_TOOL_UNAVAILABLE'; });
    let closed = false;
    if (input) {
      child.stdin.on('error', () => { closed = true; });
      // A catalog reader can close stdin early. Do not destroy the shared archive handle.
      void (async () => {
        const buffer = Buffer.alloc(64 * 1024);
        let position = 0;
        try {
          while (!closed) {
            const {bytesRead} = await input.read(buffer, 0, buffer.length, position);
            if (closed) break;
            if (!bytesRead) { child.stdin.end(); break; }
            position += bytesRead;
            await new Promise(acceptWrite => child.stdin.write(buffer.subarray(0, bytesRead), error => {
              if (error) closed = true;
              acceptWrite();
            }));
          }
        } catch { if (!closed) stop('PRIVATE_ARCHIVE_READ_FAILED'); }
      })();
    }
    child.on('close', code => {
      closed = true; clearTimeout(timer); signal?.removeEventListener('abort', abort);
      if (failure || code !== 0) reject(new RestoreFailure(failure ?? 'LOCAL_COMMAND_FAILED'));
      else accept(output.trim());
    });
  });
}

export function validateImage(value, config) {
  const image = Array.isArray(value) && value.length === 1 ? value[0] : null;
  const volumePath = config.major >= 18 ? '/var/lib/postgresql' : '/var/lib/postgresql/data';
  check(image && SHA.test(image.Id) && image.RepoDigests?.some(digest => /^postgres@sha256:[a-f0-9]{64}$/.test(digest))
    && image.Config?.Env?.includes(`PG_MAJOR=${config.major}`)
    && JSON.stringify(image.Config.Entrypoint) === '["docker-entrypoint.sh"]'
    && JSON.stringify(image.Config.Cmd) === '["postgres"]'
    && exactKeys(image.Config.Volumes, [volumePath]) && !image.Config.OnBuild?.length, 'CACHED_MATCHING_MAJOR_OFFICIAL_IMAGE_REQUIRED');
  const requestedDigest = config.image.includes('@') ? config.image.replace(/:[^:@]+@/, '@') : undefined;
  if (requestedDigest) check(image.RepoDigests.includes(requestedDigest), 'EXACT_IMAGE_DIGEST_REQUIRED');
  return {imageId: image.Id, imageRepoDigest: requestedDigest ?? image.RepoDigests.find(digest => /^postgres@sha256:[a-f0-9]{64}$/.test(digest)), volumePath};
}

export function validateContainer(item, fixture) {
  check(item && item.Id === fixture.containerId && item.Name === `/${fixture.containerName}`
    && item.Config?.Labels?.[LABEL] === fixture.runId && item.Image === fixture.imageId
    && item.State?.Paused === false, 'OWNED_CONTAINER_IDENTITY_REQUIRED');
  const host = item.HostConfig;
  check(host && host.NetworkMode === 'none' && !host.Privileged && host.ReadonlyRootfs
    && Object.keys(host.PortBindings ?? {}).length === 0 && !(host.Binds ?? []).length
    && !(host.Devices ?? []).length && !(host.VolumesFrom ?? []).length
    && !host.PidMode && (!host.IpcMode || host.IpcMode === 'private') && !host.UTSMode
    && host.RestartPolicy?.Name === 'no' && host.LogConfig?.Type === 'none'
    && host.CapDrop?.includes('ALL') && !(host.CapAdd ?? []).length
    && host.SecurityOpt?.includes('no-new-privileges') && item.Config.User === 'postgres'
    && JSON.stringify(item.Config.Entrypoint) === '["docker-entrypoint.sh"]' && item.Config.Cmd?.[0] === 'postgres'
    && exactKeys(host.Tmpfs, ['/tmp', '/var/run/postgresql'])
    && Object.values(host.Tmpfs).every(options => options.includes('noexec') && options.includes('nosuid') && options.includes('nodev'))
    && item.Mounts?.length === 1 && item.Mounts[0].Type === 'volume'
    && item.Mounts[0].Name === fixture.volumeName && item.Mounts[0].Destination === fixture.volumePath,
  'OWNED_CONTAINER_ISOLATION_REQUIRED');
}

function validateVolume(records, fixture) {
  check(Array.isArray(records) && records.length === 1 && records[0].Name === fixture.volumeName
    && records[0].Labels?.[LABEL] === fixture.runId && records[0].Driver === 'local'
    && Object.keys(records[0].Options ?? {}).length === 0, 'OWNED_VOLUME_IDENTITY_REQUIRED');
}

export async function cleanupOwned(docker, fixture) {
  // Never enumerate, inspect, unpause, prune, or delete unrelated Docker services.
  const filter = `label=${LABEL}=${fixture.runId}`;
  const ids = (await docker(['container', 'ls', '--all', '--quiet', '--no-trunc', '--filter', filter], {capture: true})).split('\n').filter(Boolean);
  check(ids.length <= 1 && ids.every(id => /^[a-f0-9]{64}$/.test(id)
    && (!fixture.containerId || fixture.containerId === id)), 'CLEANUP_SCOPE_NOT_PROVEN');
  for (const id of ids) {
    const records = json(await docker(['container', 'inspect', id], {capture: true}));
    check(Array.isArray(records) && records.length === 1, 'CLEANUP_SCOPE_NOT_PROVEN');
    validateContainer(records[0], {...fixture, containerId: id});
    await docker(['container', 'rm', '--force', id]);
  }
  const volumes = (await docker(['volume', 'ls', '--quiet', '--filter', filter], {capture: true})).split('\n').filter(Boolean);
  check(volumes.length <= 1 && volumes.every(name => name === fixture.volumeName), 'CLEANUP_SCOPE_NOT_PROVEN');
  for (const name of volumes) {
    const records = json(await docker(['volume', 'inspect', name], {capture: true}));
    validateVolume(records, fixture);
    await docker(['volume', 'rm', name]);
  }
  check(!(await docker(['container', 'ls', '--all', '--quiet', '--filter', filter], {capture: true}))
    && !(await docker(['volume', 'ls', '--quiet', '--filter', filter], {capture: true})), 'OWNED_FIXTURES_REMAIN');
}

function schemaQuery() {
  const columns = Object.entries(COLUMNS).flatMap(([table, fields]) => Object.entries(fields).map(([column, type]) =>
    `'${table}.${column}', EXISTS (SELECT 1 FROM pg_catalog.pg_attribute a
      JOIN pg_catalog.pg_class c ON c.oid=a.attrelid JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
      WHERE n.nspname='public' AND c.relname='${table}' AND c.relkind='r'
      AND a.attname='${column}' AND NOT a.attisdropped AND a.atttypid='pg_catalog.${type}'::regtype)`));
  return `SELECT json_build_object('publicSchema', EXISTS (SELECT 1 FROM pg_catalog.pg_namespace WHERE nspname='public'),
    'tableCount', (SELECT count(*)::int FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
      WHERE n.nspname='public' AND c.relkind='r'), 'columns', json_build_object(${columns.join(',')}));`;
}

function countsQuery(migration) {
  const counts = TABLES.map(table => `'${table}', (SELECT count(*)::text FROM public."${table}")`);
  return `SELECT json_build_object('counts', json_build_object(${counts.join(',')}),
    'migrationRows', (SELECT count(*)::text FROM public.flyway_schema_history),
    'migrationChainMatches', (SELECT count(*)=${migration} AND count(DISTINCT version)=${migration}
      AND bool_and(success AND type='SQL' AND CASE WHEN version ~ '^[1-9][0-9]{0,5}$'
        THEN version::int BETWEEN 1 AND ${migration} ELSE false END)
      FROM public.flyway_schema_history),
    'unvalidatedConstraints', (SELECT count(*)::text FROM pg_catalog.pg_constraint c
      JOIN pg_catalog.pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public' AND NOT c.convalidated),
    'invalidIndexes', (SELECT count(*)::text FROM pg_catalog.pg_index i
      JOIN pg_catalog.pg_class c ON c.oid=i.indexrelid JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
      WHERE n.nspname='public' AND NOT i.indisvalid));`;
}

export function validateSchema(value) {
  check(exactKeys(value, ['publicSchema', 'tableCount', 'columns']) && value.publicSchema === true
    && Number.isSafeInteger(value.tableCount) && value.tableCount >= TABLES.length
    && exactKeys(value.columns, COLUMN_KEYS) && COLUMN_KEYS.every(key => value.columns[key] === true), 'TYPED_CONTROL_PLANE_SCHEMA_REQUIRED');
  return {publicTables: value.tableCount, typedColumns: COLUMN_KEYS.length, requiredTables: TABLES.length};
}

export function validateCounts(value, migration) {
  check(exactKeys(value, ['counts', 'migrationRows', 'migrationChainMatches', 'unvalidatedConstraints', 'invalidIndexes'])
    && exactKeys(value.counts, TABLES) && TABLES.every(table => typeof value.counts[table] === 'string' && COUNT.test(value.counts[table]))
    && value.migrationChainMatches === true && value.migrationRows === String(migration)
    && value.counts.flyway_schema_history === String(migration)
    && value.unvalidatedConstraints === '0' && value.invalidIndexes === '0', 'MIGRATION_COUNTS_OR_INTEGRITY_CHECK_FAILED');
  check(['platform_customers', 'platform_users', 'deployment_target_profiles'].every(table => BigInt(value.counts[table]) > 0n), 'CORE_CONTROL_PLANE_ROWS_REQUIRED');
  return {counts: Object.fromEntries(TABLES.map(table => [table, value.counts[table]])), migrationRows: value.migrationRows,
    migrationChainMatches: true, unvalidatedConstraints: '0', invalidIndexes: '0'};
}

export async function verifyControlPlaneRestore(config, {runCommand = execute, socketProbe = assertLocalSocket, signal} = {}) {
  const directory = await mkdtemp('/private/tmp/01036-local-control-plane-restore-');
  await chmod(directory, 0o700);
  const report = {status: 'RUNNING', actualRestoreCompleted: false, expectedPostgresMajor: config.major,
    expectedMigration: config.migration, providerCalls: 0, existingDatabaseConnections: 0,
    isolation: 'LOCAL_DOCKER_NETWORK_NONE_NO_PORTS_NO_BACKEND_NO_WORKERS', cleanup: 'NOT_CREATED', checks: [],
    boundary: 'Local control-plane database recovery only; no hosted recovery, encryption-key availability, application bootstrap, runtime/vector/object-store or whole-topology acceptance.'};
  const runId = randomUUID();
  const fixture = {runId, containerName: `cp-restore-${runId}`, volumeName: `cp-restore-data-${runId}`};
  let archive, fixturesAttempted = false, cleanupPhase = false;
  const docker = (args, options = {}) => {
    check(cleanupPhase || !signal?.aborted, 'LOCAL_RESTORE_CANCELLED');
    return runCommand('docker', ['--host', config.dockerHost, ...args], {...options, signal: cleanupPhase ? undefined : signal});
  };
  try {
    archive = await privateDump(config.dump);
    report.archive = {bytes: archive.bytes, sha256: await archiveHash(archive.file)};
    report.checks.push('PRIVATE_CUSTOM_ARCHIVE');
    await socketProbe(config.dockerHost);
    report.checks.push('EXPLICIT_LOCAL_DOCKER_SOCKET');
    let cached;
    try { cached = await docker(['image', 'inspect', config.image], {capture: true}); }
    catch { throw new RestoreFailure('CACHED_MATCHING_MAJOR_IMAGE_UNAVAILABLE_NO_PULL'); }
    Object.assign(fixture, validateImage(json(cached), config));
    report.imageId = fixture.imageId;
    report.imageRepoDigest = fixture.imageRepoDigest;
    report.checks.push('CACHED_MATCHING_MAJOR_IMAGE_NO_PULL');
    fixturesAttempted = true;
    const volume = await docker(['volume', 'create', '--driver', 'local', '--label', `${LABEL}=${runId}`, fixture.volumeName], {capture: true});
    check(volume === fixture.volumeName, 'OWNED_VOLUME_CREATION_NOT_PROVEN');
    validateVolume(json(await docker(['volume', 'inspect', fixture.volumeName], {capture: true})), fixture);
    const id = await docker(['container', 'create', '--pull=never', '--name', fixture.containerName,
      '--label', `${LABEL}=${runId}`, '--network=none', '--restart=no', '--no-healthcheck', '--log-driver=none',
      '--read-only', '--user=postgres', '--cap-drop=ALL', '--security-opt=no-new-privileges',
      '--pids-limit=128', '--memory=768m', '--cpus=1',
      '--tmpfs', '/tmp:rw,noexec,nosuid,nodev,size=512m,mode=1777',
      '--tmpfs', '/var/run/postgresql:rw,noexec,nosuid,nodev,size=16m,mode=1777',
      '--mount', `type=volume,source=${fixture.volumeName},target=${fixture.volumePath}`,
      '--env', `POSTGRES_DB=${DATABASE}`, '--env', `POSTGRES_USER=${USER}`,
      '--env', `POSTGRES_PASSWORD=${randomUUID()}`, '--env', 'POSTGRES_INITDB_ARGS=--auth-local=trust --auth-host=reject',
      '--env', `PGDATA=${fixture.volumePath}/01036-data`, '--entrypoint=docker-entrypoint.sh', fixture.imageId,
      'postgres', '-c', 'listen_addresses=', '-c', `unix_socket_directories=${SOCKET}`,
      '-c', 'log_min_messages=panic', '-c', 'log_min_error_statement=panic', '-c', 'log_error_verbosity=terse',
      '-c', 'shared_preload_libraries=', '-c', 'max_worker_processes=0'], {capture: true});
    check(/^[a-f0-9]{64}$/.test(id), 'OWNED_CONTAINER_CREATION_NOT_PROVEN');
    fixture.containerId = id;
    const [item] = json(await docker(['container', 'inspect', id], {capture: true}));
    validateContainer(item, fixture);
    check(item.State.Status === 'created', 'FRESH_UNSTARTED_CONTAINER_REQUIRED');
    report.checks.push('NEW_OWNED_NETWORK_DISABLED_FIXTURES');
    await docker(['container', 'start', id]);
    let ready = false;
    for (let attempt = 0; attempt < 40 && !ready; attempt++) {
      try {
        // pg_isready alone can accept the entrypoint's temporary initialization server.
        if (await docker(['exec', id, 'cat', '/proc/1/comm'], {capture: true}) === 'postgres') {
          await docker(['exec', id, 'pg_isready', '--host', SOCKET, '--username', USER, '--dbname', DATABASE], {timeout: 5000}); ready = true;
        } else await new Promise(accept => setTimeout(accept, 500));
      }
      catch { check(!signal?.aborted, 'LOCAL_RESTORE_CANCELLED'); await new Promise(accept => setTimeout(accept, 500)); }
    }
    check(ready, 'ISOLATED_POSTGRES_NOT_READY');
    const catalog = await docker(['exec', '--interactive', id, 'pg_restore', '--list'], {input: archive.file, capture: true, timeout: 60000});
    const from = /^;\s+Dumped from database version:\s*([0-9]+)(?:\.[0-9]+)*(?:\s.*)?$/m.exec(catalog);
    const by = /^;\s+Dumped by pg_dump version:\s*([0-9]+)(?:\.[0-9]+)*(?:\s.*)?$/m.exec(catalog);
    check(from && by && Number(from[1]) === config.major && Number(by[1]) === config.major
      && /^;\s+Format:\s*CUSTOM\s*$/m.test(catalog), 'ARCHIVE_AND_RESTORE_MAJOR_MUST_MATCH');
    report.archive.sourceMajor = Number(from[1]);
    report.archive.dumpToolMajor = Number(by[1]);
    const psql = sql => docker(['exec', id, 'psql', '-X', '--no-password', '--host', SOCKET, '--username', USER,
      '--dbname', DATABASE, '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1', '--command', sql], {capture: true, timeout: 60000});
    const server = json(await psql("SELECT json_build_object('major', current_setting('server_version_num')::int / 10000, 'empty', NOT EXISTS (SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','v','m','f','p')));"));
    check(exactKeys(server, ['major', 'empty']) && server.major === config.major && server.empty === true, 'FRESH_MATCHING_MAJOR_DATABASE_REQUIRED');
    report.checks.push('ARCHIVE_TOOL_SERVER_MAJOR_MATCH', 'EMPTY_ISOLATED_DATABASE');
    try {
      await docker(['exec', '--interactive', id, 'pg_restore', '--exit-on-error', '--single-transaction',
        '--no-owner', '--no-privileges', '--no-tablespaces', '--no-security-labels', '--no-subscriptions',
        '--no-password', '--host', SOCKET, '--username', USER, '--dbname', DATABASE], {input: archive.file, timeout: 180000});
    } catch { throw new RestoreFailure('PG_RESTORE_FAILED_DIAGNOSTICS_REDACTED'); }
    report.actualRestoreCompleted = true;
    report.checks.push('FULL_TRANSACTIONAL_PG_RESTORE');
    report.schema = validateSchema(json(await psql(schemaQuery())));
    report.integrity = validateCounts(json(await psql(countsQuery(config.migration))), config.migration);
    report.checks.push('TYPED_CONTROL_PLANE_SCHEMA', 'EXACT_SUCCESSFUL_MIGRATION_CHAIN', 'AGGREGATE_COUNTS_AND_VALID_CONSTRAINTS_INDEXES');
    check(await archiveHash(archive.file) === report.archive.sha256, 'ARCHIVE_CHANGED_DURING_RESTORE');
    report.checks.push('SOURCE_ARCHIVE_HASH_UNCHANGED');
    report.status = 'PASS';
  } catch (error) { report.status = 'FAIL'; report.code = safeCode(error); }
  finally {
    cleanupPhase = true;
    if (fixturesAttempted) {
      try { await cleanupOwned(docker, fixture); report.cleanup = 'OWNED_FIXTURES_REMOVED'; }
      catch { report.status = 'FAIL'; report.cleanup = 'NOT_PROVEN'; report.cleanupCode = 'OWNED_CLEANUP_NOT_PROVEN'; }
    }
    if (archive) {
      try { await archive.file.close(); } catch { report.status = 'FAIL'; report.code = 'PRIVATE_ARCHIVE_CLOSE_FAILED'; }
    }
    await writeFile(resolve(directory, 'report.json'), JSON.stringify(report, null, 2) + '\n', {mode: 0o600, flag: 'wx'});
  }
  return {report, reportPath: resolve(directory, 'report.json')};
}

async function main() {
  if (process.argv.length === 3 && process.argv[2] === '--help') {
    console.log('RUN_01036_LOCAL_CONTROL_PLANE_RESTORE=true node run-local-control-plane-restore.mjs --dump /absolute/private/archive.dump --docker-host unix:///absolute/local/docker.sock --postgres-major 18 --expected-migration 191 [--image postgres:18-alpine@sha256:<cached-digest>]\nMigration 191 is the expectation for the pre-V192 dumps, not current-head recovery. The supplied PostgreSQL major selects a cached image; official pg_restore --list must confirm the actual archive source/tool major before restoring data. Requires a private 0600 custom archive in a 0700 directory and a cached official matching-major image. No image pulls, remote endpoints, existing database targets, backend/worker startup, or retained Docker fixtures. Only a private 0600 aggregate report is retained; raw diagnostics are discarded. This does not certify hosted or whole-topology recovery.');
    return;
  }
  process.umask(0o077);
  const config = configuration(process.argv.slice(2));
  const controller = new AbortController();
  const abort = () => controller.abort();
  process.on('SIGINT', abort); process.on('SIGTERM', abort);
  const watchdog = setTimeout(abort, 300000);
  try {
    const {report, reportPath} = await verifyControlPlaneRestore(config, {signal: controller.signal});
    console.log(JSON.stringify({status: report.status, code: report.code, actualRestoreCompleted: report.actualRestoreCompleted,
      cleanup: report.cleanup, cleanupCode: report.cleanupCode, reportPath}));
    process.exitCode = report.status === 'PASS' ? 0 : 1;
  } finally { clearTimeout(watchdog); process.off('SIGINT', abort); process.off('SIGTERM', abort); }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => { console.error(safeCode(error)); process.exitCode = 1; });
}
