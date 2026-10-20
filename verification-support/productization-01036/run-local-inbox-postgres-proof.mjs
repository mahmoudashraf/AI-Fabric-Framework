import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {createHash, randomBytes, randomUUID} from 'node:crypto';
import {chmod, mkdir, readFile, readdir, realpath, stat, writeFile} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

export const IMAGE = 'ghcr.io/mahmoudashraf/loomai-basic-inbox-service@sha256:d74871880950f09674e0558cdfb09bdadcce050e89ae7b33312dad7069ac3759';
export const SOURCE = '2035dc36082fefccbfd3a757d38e493fa8f001fc';
export const LABEL = 'pro.loomai.local-inbox-postgres-proof';
export const TABLES = Object.freeze(['flyway_schema_history', 'inbox_callback_outbox', 'inbox_conversation',
  'inbox_dispatch_receipt', 'inbox_message', 'inbox_operator_operation',
  'loomai_managed_dependency_operation', 'loomai_managed_dependency_state']);
const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, '../..');
const PRODUCT = 'product-services/basic-loomai-inbox-service';
const JDK = '/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home';
const sha = value => createHash('sha256').update(value).digest('hex');
const check = (value, code) => { if (!value) throw new Error(code); };

export function configuration(args, env = process.env) {
  check(env.RUN_01036_LOCAL_INBOX_POSTGRES_PROOF === 'true', 'EXPLICIT_LOCAL_PROOF_OPT_IN_REQUIRED');
  const values = {};
  for (let index = 0; index < args.length; index += 2) {
    const key = args[index];
    check(['--docker-host', '--output-root', '--allow-anonymous-exact-pull'].includes(key)
      && !Object.hasOwn(values, key) && typeof args[index + 1] === 'string', 'EXACT_ARGUMENTS_REQUIRED');
    values[key] = args[index + 1];
  }
  const host = values['--docker-host'];
  check(/^unix:\/\/\/[a-zA-Z0-9_./-]+$/.test(host ?? '')
    && resolve(host.slice(7)) === host.slice(7), 'LOCAL_UNIX_DOCKER_REQUIRED');
  const root = values['--output-root'];
  check(/^\/private\/tmp\/[a-zA-Z0-9_-]+$/.test(root ?? ''), 'NEW_PRIVATE_TMP_ROOT_REQUIRED');
  const pull = values['--allow-anonymous-exact-pull'] ?? 'false';
  check(['true', 'false'].includes(pull), 'BOOLEAN_PULL_OPT_IN_REQUIRED');
  return {host, root, pull: pull === 'true'};
}

export function validateInboxImage(image) {
  check(image?.RepoDigests?.includes(IMAGE) && image.Os === 'linux' && image.Architecture === 'amd64'
    && image.Config?.Labels?.['org.opencontainers.image.revision'] === SOURCE
    && image.Config.Labels['org.opencontainers.image.source'] === 'https://github.com/mahmoudashraf/AI-Fabric-Framework'
    && !image.Config.OnBuild?.length, 'EXACT_INBOX_IMAGE_LINEAGE_REQUIRED');
  return {id: image.Id, digest: IMAGE, source: SOURCE, createdAt: image.Created, labels: image.Config.Labels};
}

export function validatePostgresImage(image) {
  check(/^sha256:[a-f0-9]{64}$/.test(image?.Id ?? '') && image.Config?.Env?.includes('PG_MAJOR=16')
    && image.RepoDigests?.some(value => /^postgres@sha256:[a-f0-9]{64}$/.test(value))
    && JSON.stringify(image.Config.Entrypoint) === '["docker-entrypoint.sh"]'
    && !image.Config.OnBuild?.length, 'CACHED_OFFICIAL_POSTGRES_16_REQUIRED');
  return {id: image.Id, digests: image.RepoDigests, createdAt: image.Created};
}

export function owned(record, type, id, runId) {
  const labels = type === 'container' ? record?.Config?.Labels : record?.Labels;
  check(labels?.[LABEL] === runId && (type === 'volume' ? record.Name : record.Id) === id,
    'EXACT_OWNED_RESOURCE_REQUIRED');
}

export function validateIsolation(record, {network, namespace, imageId, runId, id}) {
  owned(record, 'container', id, runId);
  const host = record.HostConfig;
  check(record.Image === imageId && host?.NetworkMode === (namespace ? `container:${namespace}` : network)
    && !host.Privileged && host.ReadonlyRootfs && host.NanoCpus === 1000000000
    && host.Memory > 0 && host.Memory <= 640 * 1024 ** 2 && host.PidsLimit === 160
    && host.CapDrop?.includes('ALL') && !host.CapAdd?.length
    && host.SecurityOpt?.includes('no-new-privileges') && !host.Devices?.length
    && !Object.keys(host.PortBindings ?? {}).length && !host.PublishAllPorts
    && host.RestartPolicy?.Name === 'no' && !host.PidMode && !host.UTSMode,
  'OWNED_INTERNAL_RESOURCE_BOUNDS_REQUIRED');
  if (!namespace) check(JSON.stringify(host.Dns) === '["127.0.0.1"]'
    && Object.keys(record.NetworkSettings.Networks).length === 1, 'NO_EXTERNAL_DNS_OR_NETWORK_REQUIRED');
  return {id, imageId, user: record.Config.User, host: {networkMode: host.NetworkMode,
    memory: host.Memory, nanoCpus: host.NanoCpus, pidsLimit: host.PidsLimit, readOnly: host.ReadonlyRootfs,
    dns: host.Dns, portBindings: host.PortBindings, capDrop: host.CapDrop, securityOpt: host.SecurityOpt},
    mounts: record.Mounts.map(item => ({type: item.Type, destination: item.Destination, rw: item.RW}))};
}

export function localUrl(value) {
  const url = new URL(value);
  check(url.protocol === 'http:' && url.hostname === '127.0.0.1' && !url.username && !url.password,
    'LOOPBACK_HTTP_ONLY');
  return url;
}

export function command(program, args, {input, timeout = 30000, cwd = REPO, mergeStderr = false} = {}) {
  // Never inherit auth, proxy, PG, Docker credential helpers, Java options, or preload hooks.
  const env = Object.fromEntries(['PATH', 'LANG', 'LC_ALL', 'TMPDIR'].filter(key => process.env[key] !== undefined)
    .map(key => [key, process.env[key]]));
  return new Promise((accept, reject) => {
    const child = spawn(program, args, {env, cwd, stdio: ['pipe', 'pipe', 'pipe']});
    const out = [], err = [];
    let size = 0, problem;
    const kill = code => { problem = code; child.kill('SIGKILL'); };
    const timer = setTimeout(() => kill('COMMAND_TIMEOUT'), timeout);
    for (const [stream, destination] of [[child.stdout, out], [child.stderr, err]]) {
      stream.on('data', bytes => { size += bytes.length; if (size > 16 * 1024 * 1024) kill('COMMAND_OUTPUT_LIMIT'); else destination.push(bytes); });
    }
    child.on('error', error => { problem = error.code; });
    child.stdin.on('error', () => {});
    child.stdin.end(input);
    child.on('close', code => {
      clearTimeout(timer);
      const result = {code, stdout: Buffer.concat(out), stderr: Buffer.concat(err)};
      if (problem || code !== 0) {
        const error = new Error(problem ?? 'LOCAL_COMMAND_FAILED'); error.result = result; reject(error);
      } else accept(mergeStderr ? Buffer.concat([result.stdout, result.stderr]) : result.stdout);
    });
  });
}

async function http(base, path, {method = 'GET', body, headers = {}, expected = 200} = {}, transport) {
  const url = localUrl(base + path);
  check(typeof transport === 'function', 'OWNED_EXEC_TRANSPORT_REQUIRED');
  const response = await transport(url, method, {'Content-Type': 'application/json', ...headers}, body);
  const text = response.text;
  let value; try { value = JSON.parse(text); } catch { value = text; }
  if (response.status !== expected) {
    const error = new Error(`LOCAL_HTTP_${response.status}_EXPECTED_${expected}`);
    error.response = {path, status: response.status, body: value}; throw error;
  }
  return value;
}

async function poll(read, accept, label, timeout = 90000) {
  const end = Date.now() + timeout;
  let last;
  do {
    try { last = await read(); if (accept(last)) return last; } catch (error) {
      if (error.message === 'PROOF_INTERRUPTED_OR_DEADLINE') throw error;
      last = {error: error.message, response: error.response};
    }
    await new Promise(done => setTimeout(done, 350));
  } while (Date.now() < end);
  const error = new Error(`TIMED_OUT_${label}`); error.last = last; throw error;
}

export async function run(config) {
  process.umask(0o077);
  await mkdir(config.root, {mode: 0o700}); // No overwrite of any earlier success or failure.
  check(await realpath(config.root) === config.root, 'SYMLINK_ROOT_FORBIDDEN');
  const socket = await stat(config.host.slice(7));
  check(socket.isSocket() && [0, process.getuid()].includes(socket.uid), 'OWNED_LOCAL_SOCKET_REQUIRED');
  const runId = randomUUID();
  const prefix = `inbox-proof-${runId}`;
  const resources = [];
  let interrupted = false, cleaning = false;
  const deadline = Date.now() + 12 * 60 * 1000;
  const interrupt = () => { interrupted = true; };
  process.once('SIGINT', interrupt); process.once('SIGTERM', interrupt);
  const bounded = () => check(cleaning || (!interrupted && Date.now() < deadline), 'PROOF_INTERRUPTED_OR_DEADLINE');
  const report = {status: 'RUNNING', scope: 'LOCAL_POSTGRES_INBOX_SUBSYSTEM_ONLY_NOT_P5', runId,
    startedAt: new Date().toISOString(), image: IMAGE, source: SOURCE, facts: [], failures: [], cleanup: [], requests: []};
  const save = async (name, value) => writeFile(resolve(config.root, name),
    typeof value === 'string' || Buffer.isBuffer(value) ? value : JSON.stringify(value, null, 2) + '\n', {mode: 0o600});
  const fact = async (name, details = {}) => { report.facts.push({name, observedAt: new Date().toISOString(), ...details}); await save('report.json', report); };
  const dockerConfig = resolve(config.root, 'anonymous-docker');
  await mkdir(dockerConfig, {mode: 0o700});
  await writeFile(resolve(dockerConfig, 'config.json'), '{}\n', {mode: 0o600});
  const docker = (...args) => { bounded(); return command('docker', ['--config', dockerConfig, '--host', config.host, ...args]); };
  const inspect = async (type, id) => JSON.parse((await docker(type, 'inspect', id)).toString())[0];
  const resource = async (type, name, args) => {
    try {
      const result = (await docker(...args)).toString().trim();
      const id = type === 'volume' ? name : result;
      resources.push({type, id, name});
      owned(await inspect(type, id), type, id, runId);
      return id;
    } catch (error) {
      // A timed-out create can have succeeded. Recover only this exact unique name and label.
      if (!resources.some(item => item.name === name)) {
        try {
          const row = await inspect(type, name);
          const id = type === 'volume' ? row.Name : row.Id;
          owned(row, type, id, runId);
          check(row.Name === (type === 'container' ? `/${name}` : name), 'EXACT_RESOURCE_NAME_REQUIRED');
          resources.push({type, id, name});
        } catch { report.failures.push({message: 'CREATE_OUTCOME_NOT_RECOVERED', type, name}); }
      }
      throw error;
    }
  };
  let app, pg, stub;
  const request = (base, path, options) => http(base, path, options, async (url, method, headers, body) => {
    bounded();
    const input = [method, url.href, String(Object.keys(headers).length), ...Object.entries(headers).flat(),
      Buffer.from(body === undefined ? '' : JSON.stringify(body)).toString('base64'), ''].join('\n');
    const raw = (await command('docker', ['--config', dockerConfig, '--host', config.host, 'exec', '-i', stub,
      'java', '-Xmx32m', '-XX:ActiveProcessorCount=1', '-cp', '/proof', 'InboxLocalHttpClient'], {input})).toString();
    const split = raw.indexOf('\n');
    check(split > 0, 'TYPED_HTTP_OUTPUT_REQUIRED');
    const response = {status: Number(raw.slice(0, split)), text: raw.slice(split + 1)};
    report.requests.push({method, path, bodySha256: body === undefined ? null : sha(JSON.stringify(body)),
      response, observedAt: new Date().toISOString()});
    return response;
  });
  const sql = async query => (await docker('exec', '-i', pg, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'proof', '-d', 'inbox_proof', '-c', query)).toString().trim();
  const snapshot = async name => {
    const tables = JSON.parse(await sql("SELECT json_agg(tablename ORDER BY tablename) FROM pg_tables WHERE schemaname='public'"));
    assert.deepEqual(tables, [...TABLES].sort());
    const data = {};
    for (const table of TABLES) {
      data[table] = JSON.parse(await sql(`SELECT COALESCE(json_agg(row ORDER BY row::text), '[]'::json) FROM (SELECT to_jsonb(t) AS row FROM ${table} t) rows`));
    }
    await save(`${name}.json`, data); return data;
  };
  try {
    const harnessPins = {};
    for (const file of ['run-local-inbox-postgres-proof.mjs', 'InboxLocalCallbackStub.java', 'test/local-inbox-postgres-proof.test.mjs'])
      harnessPins[file] = sha(await readFile(resolve(HERE, file)));
    await save('harness-inputs.json', {harnessPins, cli: {dockerHost: config.host, anonymousPullOptIn: config.pull},
      node: process.version, deadlineSeconds: 720});
    const lineage = (await command('git', ['diff', '--name-only', SOURCE, '--', PRODUCT])).toString().trim().split('\n').filter(Boolean);
    assert.deepEqual(lineage.filter(path => path !== `${PRODUCT}/deployment/managed-dependency-spec.template.json`), []);
    const files = (await command('git', ['ls-tree', '-r', '--name-only', SOURCE, '--', PRODUCT])).toString().trim().split('\n');
    const pins = {};
    for (const path of files) if (!path.endsWith('deployment/managed-dependency-spec.template.json')) pins[path] = sha(await readFile(resolve(REPO, path)));
    await save('source-lineage.json', {source: SOURCE, differences: lineage, unchangedFileSha256: pins});
    let inbox;
    try { inbox = await inspect('image', IMAGE); }
    catch (error) {
      check(config.pull, 'EXACT_IMAGE_NOT_CACHED_ANONYMOUS_PULL_NOT_OPTED_IN');
      await docker('pull', '--platform', 'linux/amd64', IMAGE);
      inbox = await inspect('image', IMAGE);
    }
    report.inboxImage = validateInboxImage(inbox);
    const pgImage = await inspect('image', 'postgres:16-alpine');
    report.postgresImage = validatePostgresImage(pgImage);
    await fact('EXACT_IMAGES_AND_SOURCE_LINEAGE');
    const classes = resolve(config.root, 'stub-classes');
    await mkdir(classes, {mode: 0o755});
    await chmod(classes, 0o755);
    await command(`${JDK}/bin/javac`, ['--release', '21', '-d', classes, resolve(HERE, 'InboxLocalCallbackStub.java')]);
    for (const file of await readdir(classes)) await chmod(resolve(classes, file), 0o644);
    const label = `${LABEL}=${runId}`;
    const network = await resource('network', `${prefix}-net`, ['network', 'create', '--internal', '--label', label, `${prefix}-net`]);
    check((await inspect('network', network)).Internal === true, 'INTERNAL_NETWORK_REQUIRED');
    const volume = `${prefix}-pg`;
    await resource('volume', volume, ['volume', 'create', '--label', label, volume]);
    const secrets = Object.fromEntries(['pg', 'provider', 'operator', 'outsider', 'signing', 'encryption', 'managed', 'control']
      .map(name => [name, randomBytes(32).toString('hex')]));
    report.syntheticKeySha256 = Object.fromEntries(Object.entries(secrets).map(([key, value]) => [key, sha(value)]));
    const envFile = async (name, env) => {
      const path = resolve(config.root, name);
      await save(name, Object.entries(env).map(([key, value]) => `${key}=${value}`).join('\n') + '\n');
      return path;
    };
    const harden = ['--pull', 'never', '--platform', 'linux/amd64', '--label', label, '--restart', 'no', '--cpus', '1',
      '--pids-limit', '160', '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges', '--read-only', '--log-opt', 'max-size=8m', '--log-opt', 'max-file=1'];
    const pgEnv = await envFile('postgres.synthetic.env', {POSTGRES_USER: 'proof', POSTGRES_DB: 'inbox_proof', POSTGRES_PASSWORD: secrets.pg});
    pg = await resource('container', `${prefix}-pg`, ['run', '-d', '--name', `${prefix}-pg`, ...harden,
      '--memory', '384m', '--user', 'postgres', '--network', network, '--dns', '127.0.0.1',
      '--mount', `type=volume,src=${volume},dst=/var/lib/postgresql/data`, '--tmpfs', '/var/run/postgresql:rw,noexec,nosuid,nodev,uid=70,gid=70',
      '--tmpfs', '/tmp:rw,noexec,nosuid,nodev,size=32m', '--env-file', pgEnv, pgImage.Id]);
    await poll(() => sql('SELECT 1'), value => value === '1', 'POSTGRES');
    const pgIp = Object.values((await inspect('container', pg)).NetworkSettings.Networks)[0].IPAddress;
    check(/^\d+\.\d+\.\d+\.\d+$/.test(pgIp), 'OWNED_PG_IP_REQUIRED');
    const stubEnv = await envFile('stub.synthetic.env', {STUB_CONTROL_KEY: secrets.control, STUB_SIGNING_KEY: secrets.signing});
    stub = await resource('container', `${prefix}-stub`, ['run', '-d', '--name', `${prefix}-stub`, ...harden,
      '--memory', '256m', '--network', network, '--dns', '127.0.0.1', '--env-file', stubEnv,
      '--mount', `type=bind,src=${classes},dst=/proof,readonly`, '--tmpfs', '/tmp:rw,noexec,nosuid,nodev,size=16m',
      '--entrypoint', 'java', inbox.Id, '-Xmx64m', '-XX:ActiveProcessorCount=1', '-cp', '/proof', 'InboxLocalCallbackStub']);
    const base = 'http://127.0.0.1:8085';
    const stubBase = 'http://127.0.0.1:8090';
    const control = (path, options = {}) => request(stubBase, path, {...options, headers: {'X-Proof-Control': secrets.control}});
    await poll(() => control('/events'), value => Array.isArray(value), 'STUB');
    const ids = {customerId: 'synthetic-customer', tenantId: 'synthetic-tenant', deploymentId: 'synthetic-deployment',
      targetProfileId: 'synthetic-target', versionId: 'synthetic-version', slot: 'staging', serviceRef: 'synthetic-inbox',
      accountId: 'synthetic-account', bindingId: 'synthetic-binding'};
    const appEnv = {AI_ENABLED: 'false', JAVA_OPTS: '-Xmx384m -XX:ActiveProcessorCount=1',
      BASIC_INBOX_JDBC_URL: `jdbc:postgresql://${pgIp}:5432/inbox_proof`, BASIC_INBOX_JDBC_USERNAME: 'proof', BASIC_INBOX_JDBC_PASSWORD: secrets.pg,
      BASIC_INBOX_BINDING_ID: ids.bindingId, BASIC_INBOX_TENANT_ID: ids.tenantId, BASIC_INBOX_DEPLOYMENT_ID: ids.deploymentId,
      BASIC_INBOX_PROVIDER_API_KEY: secrets.provider, BASIC_INBOX_CALLBACK_SIGNING_SECRET: secrets.signing,
      BASIC_INBOX_DATA_ENCRYPTION_SECRET: secrets.encryption, BASIC_INBOX_CONNECTOR_CALLBACK_URL: 'http://localhost:8090/callback',
      BASIC_INBOX_OPERATORS: `synthetic-operator|Local Operator|${secrets.operator}|${ids.bindingId};synthetic-outsider|Other Operator|${secrets.outsider}|other-binding`,
      BASIC_INBOX_MAX_CALLBACK_ATTEMPTS: '2', BASIC_INBOX_CALLBACK_RETRY_DELAY: '1s', BASIC_INBOX_CALLBACK_POLL_MS: '200',
      BASIC_INBOX_REQUEST_TIMEOUT: '2s', BASIC_INBOX_RETENTION: '365d', BASIC_INBOX_RETENTION_CRON: '-',
      LOOMAI_MANAGED_DEPENDENCY_KEY: secrets.managed};
    for (const [key, value] of Object.entries(ids)) appEnv[`LOOMAI_MANAGED_DEPENDENCY_${key.replace(/[A-Z]/g, letter => `_${letter}`).toUpperCase()}`] = value;
    const appEnvPath = await envFile('inbox.synthetic.env', appEnv);
    app = await resource('container', `${prefix}-app`, ['run', '-d', '--name', `${prefix}-app`, ...harden,
      '--memory', '640m', '--network', `container:${stub}`, '--tmpfs', '/tmp:rw,noexec,nosuid,nodev,size=64m',
      '--env-file', appEnvPath, inbox.Id]);
    const isolation = [];
    for (const [id, imageId, namespace] of [[pg, pgImage.Id, null], [stub, inbox.Id, null], [app, inbox.Id, stub]]) {
      isolation.push(validateIsolation(await inspect('container', id), {network, namespace, imageId, runId, id}));
    }
    await save('isolation.actual.json', {internalNetwork: {id: network, internal: (await inspect('network', network)).Internal}, containers: isolation});
    await fact('INTERNAL_NETWORK_NO_PUBLISHED_PORTS_BOUNDED_CONTAINERS');
    const ready = () => poll(() => request(base, '/actuator/health'), body => body.status === 'UP', 'INBOX_READY', 150000);
    await ready();
    await docker('cp', `${app}:/app/basic-loomai-inbox.jar`, resolve(config.root, 'exact-image-inbox.jar'));
    const migrationPins = {};
    for (const file of (await readdir(resolve(REPO, PRODUCT, 'src/main/resources/db/basic-inbox-migration'))).sort()) {
      const bytes = await command('/usr/bin/unzip', ['-p', resolve(config.root, 'exact-image-inbox.jar'), `BOOT-INF/classes/db/basic-inbox-migration/${file}`]);
      assert.equal(sha(bytes), sha(await readFile(resolve(REPO, PRODUCT, 'src/main/resources/db/basic-inbox-migration', file))));
      migrationPins[file] = sha(bytes);
    }
    await save('migration-image-lineage.json', migrationPins);
    const initial = await snapshot('migrated-empty');
    assert.deepEqual(initial.flyway_schema_history.filter(row => row.version !== null).map(row => row.version).sort(), ['1', '2', '3', '4', '5']);
    check(initial.flyway_schema_history.every(row => row.success), 'ALL_MIGRATIONS_SUCCESS_REQUIRED');
    const server = await sql('SELECT version()');
    check(server.startsWith('PostgreSQL 16.'), 'ACTUAL_POSTGRES_REQUIRED');
    await fact('DATABASE_JDBC_ACTUAL_PG_V1_V5', {server, migrationPins});
    const providerHeaders = {'X-LoomAI-Inbox-Key': secrets.provider};
    const operatorHeaders = {'X-LoomAI-Operator-Key': secrets.operator};
    const managedHeaders = {'X-LoomAI-Managed-Dependency-Key': secrets.managed};
    const dispatch = (body, expected = 202) => request(base, '/api/conversation-channel/v1/dispatches', {method: 'POST', body, headers: providerHeaders, expected});
    const operator = (path, body, expected = 200) => request(base, `/api/operator/${path}`, {method: body === undefined ? 'GET' : 'POST', body, headers: operatorHeaders, expected});
    const lifecycleBody = (command, operationKey) => ({contractVersion: 'loomai-managed-dependency-lifecycle/v1', command,
      initializationContract: 'inbox-callback-canary/v1', ...ids, operationKey});
    const lifecycle = (command, key, expected = 200) => request(base, '/internal/loomai-managed-dependency/v1/lifecycle',
      {method: 'POST', body: lifecycleBody(command, key), headers: managedHeaders, expected});
    const emptyUnused = await lifecycle('VERIFY_UNUSED', 'empty-unused-local');
    assert.equal(emptyUnused.state, 'UNUSED'); assert.equal(emptyUnused.activeWork, 0);
    assert.equal((await lifecycle('VERIFY_UNUSED', 'empty-unused-local')).replay, true);
    assert.equal((await lifecycle('INITIALIZE', 'initialize-local')).state, 'READY');
    assert.equal((await lifecycle('INITIALIZE', 'initialize-local')).replay, true);
    await request(base, '/api/operator/me', {headers: {'X-LoomAI-Operator-Key': 'synthetic-invalid'}, expected: 401});
    const open = handover => ({dispatchId: `open-${handover}`, type: 'OPEN_HANDOVER', scope: {tenantId: ids.tenantId,
      deploymentId: ids.deploymentId, conversationId: 'synthetic-conversation', ownerId: 'synthetic-owner'},
      handoverId: handover, channelBindingId: ids.bindingId, expectedControlVersion: 1, messageId: null, text: null,
      attachmentRefs: [], transcript: [{messageId: 'source-user-1', role: 'USER', actorRef: 'synthetic-user',
        safeActorDisplayName: 'Local User', text: 'Synthetic intake only', attachmentRefs: [], createdAt: new Date().toISOString()}],
      consentedIntake: {name: 'Synthetic User'}, createdAt: new Date().toISOString()});
    const first = open('synthetic-return');
    const accepted = await dispatch(first); assert.equal(accepted.accepted, true);
    assert.deepEqual(await dispatch(first), accepted);
    assert.equal((await dispatch({...first, consentedIntake: {name: 'Changed'}}, 409)).code, 'DISPATCH_ID_PAYLOAD_CONFLICT');
    for (const field of ['tenantId', 'deploymentId']) assert.equal((await dispatch({...first, dispatchId: `wrong-${field}`,
      scope: {...first.scope, [field]: 'foreign'}}, 409)).code, 'DEPLOYMENT_SCOPE_MISMATCH');
    assert.equal((await dispatch({...first, dispatchId: 'wrong-binding', channelBindingId: 'foreign'}, 409)).code, 'CHANNEL_BINDING_MISMATCH');
    await request(base, '/api/operator/conversations/synthetic-return', {headers: {'X-LoomAI-Operator-Key': secrets.outsider}, expected: 401});
    const detail = async handover => operator(`conversations/${handover}`);
    check((await detail(first.handoverId)).messages.some(row => row.content === 'Synthetic intake only'), 'ACTUAL_TRANSCRIPT_REQUIRED');
    await fact('SCOPED_DISPATCH_DEDUPE_AND_CONFLICTS');
    const claim = handover => operator(`conversations/${handover}/claim`, {idempotencyKey: `claim-${handover}`});
    await claim(first.handoverId);
    await poll(() => detail(first.handoverId), body => body.conversation.status === 'ACTIVE', 'CLAIM_CALLBACK');
    assert.equal((await claim(first.handoverId)).outcome, 'CLAIM_REPLAYED');
    const reply = (handover, text, key, expected) => operator(`conversations/${handover}/messages`, {text, idempotencyKey: key}, expected);
    await control('/mode?status=503', {method: 'POST'});
    await reply(first.handoverId, 'First synthetic reply', 'reply-1');
    await reply(first.handoverId, 'Second synthetic reply', 'reply-2');
    const callbacks = () => operator('operations/callbacks');
    const dead = await poll(callbacks, rows => rows.some(row => row.eventType === 'HUMAN_MESSAGE' && row.status === 'DEAD'), 'RETRY_EXHAUSTION');
    const deadCallback = dead.find(row => row.eventType === 'HUMAN_MESSAGE' && row.status === 'DEAD');
    const blocked = await snapshot('fifo-blocked');
    const ordered = blocked.inbox_callback_outbox.filter(row => row.handover_id === first.handoverId).sort((a, b) => a.callback_sequence - b.callback_sequence);
    assert.deepEqual(ordered.map(row => row.status), ['COMPLETED', 'DEAD', 'PENDING']);
    assert.equal(ordered[2].attempt_count, 0);
    assert.equal(deadCallback.attemptCount, 2);
    assert.equal((await reply(first.handoverId, 'Changed', 'reply-1', 409)).code, 'IDEMPOTENCY_KEY_PAYLOAD_CONFLICT');
    await fact('RETRY_DEAD_FIFO_BLOCKS_LATER_CALLBACK', {callbackId: deadCallback.callbackId});
    // Writer fence is process stop, not DISABLE (which does not fence the callback scheduler).
    await docker('stop', '--time', '20', app);
    const beforeRestart = await snapshot('before-restart');
    await docker('start', app); await ready();
    assert.deepEqual(await snapshot('after-restart'), beforeRestart);
    assert.equal((await detail(first.handoverId)).messages.filter(row => row.participantRole === 'HUMAN').length, 2);
    await fact('SAME_PG_APP_RESTART_PRESERVES_ALL_ROWS_AND_DECRYPTION');
    await control('/mode?status=200', {method: 'POST'});
    const retryPath = `operations/callbacks/${deadCallback.callbackId}/retry`;
    assert.equal((await operator(retryPath, {idempotencyKey: 'retry-1'})).outcome, 'CALLBACK_RETRY_QUEUED');
    assert.equal((await operator(retryPath, {idempotencyKey: 'retry-1'})).outcome, 'CALLBACK_RETRY_REPLAYED');
    await poll(callbacks, rows => rows.every(row => row.status === 'COMPLETED'), 'FIFO_DRAIN');
    assert.equal((await reply(first.handoverId, 'First synthetic reply', 'reply-1')).outcome, 'MESSAGE_REPLAYED');
    const eventRows = await control('/events');
    const events = eventRows.map(row => ({...row, payload: JSON.parse(Buffer.from(row.payloadBase64, 'base64').toString())}));
    check(events.every(row => row.signatureValid), 'ALL_CALLBACK_HMACS_VALID_REQUIRED');
    const successes = events.filter(row => row.responseStatus === 200);
    assert.deepEqual(successes.map(row => row.payload.type), ['HANDOVER_CLAIMED', 'HUMAN_MESSAGE', 'HUMAN_MESSAGE']);
    const attempts = events.filter(row => row.payload.type === 'HUMAN_MESSAGE');
    assert.deepEqual(attempts.map(row => row.responseStatus), [503, 503, 200, 200]);
    assert.equal(attempts[0].payload.eventId, attempts[2].payload.eventId);
    assert.deepEqual(attempts[0].payload, attempts[2].payload);
    await save('callback-events.before-restore.json', events);
    await operator(`conversations/${first.handoverId}/return-to-ai`, {idempotencyKey: 'return-1'});
    await poll(() => detail(first.handoverId), row => row.conversation.status === 'RETURNED_TO_AI', 'RETURN');
    const second = open('synthetic-close'); await dispatch(second); await claim(second.handoverId);
    await poll(() => detail(second.handoverId), row => row.conversation.status === 'ACTIVE', 'SECOND_CLAIM');
    await operator(`conversations/${second.handoverId}/close`, {idempotencyKey: 'close-1'});
    await poll(() => detail(second.handoverId), row => row.conversation.status === 'CLOSED', 'CLOSE');
    // RETURNED_TO_AI is terminal; it cannot be rewritten CLOSED to manufacture an unused proof.
    assert.equal((await dispatch({...first, dispatchId: 'close-returned', type: 'CLOSE', expectedControlVersion: 3,
      transcript: [], consentedIntake: {}}, 409)).code, 'CONTROL_DISPATCH_NOT_ALLOWED');
    assert.equal((await detail(first.handoverId)).conversation.status, 'RETURNED_TO_AI');
    await lifecycle('VERIFY_UNUSED', 'unused-with-returned-local', 409);
    assert.equal((await lifecycle('DISABLE', 'disable-local')).state, 'DISABLED');
    await operator('me', undefined, 503);
    await fact('OPERATOR_REPLY_REPLAY_RETRY_RETURN_CLOSE_AND_LIFECYCLE',
      {emptyStoreUnusedPassed: true, returnedEpisodeUnusedRejected: true, retainedTerminalStates: ['RETURNED_TO_AI', 'CLOSED']});
    await docker('stop', '--time', '20', app);
    check((await inspect('container', app)).State.Running === false, 'WRITER_STOP_REQUIRED');
    assert.equal(await sql("SELECT count(*) FROM pg_stat_activity WHERE datname='inbox_proof' AND pid<>pg_backend_pid()"), '0');
    const before = await snapshot('quiescent-before-dump');
    check(before.inbox_callback_outbox.every(row => row.status === 'COMPLETED' && row.lease_owner === null), 'QUIESCENT_CALLBACKS_REQUIRED');
    const dump = await docker('exec', pg, 'pg_dump', '-U', 'proof', '-d', 'inbox_proof', '-Fc', '--no-owner', '--no-acl');
    check(dump.subarray(0, 5).toString() === 'PGDMP', 'ACTUAL_CUSTOM_DUMP_REQUIRED');
    await save('inbox.quiescent.pgdump', dump);
    await docker('exec', pg, 'dropdb', '-U', 'proof', 'inbox_proof');
    await docker('exec', pg, 'createdb', '-U', 'proof', '-O', 'proof', 'inbox_proof');
    assert.equal(await sql("SELECT count(*) FROM pg_tables WHERE schemaname='public'"), '0');
    await command('docker', ['--config', dockerConfig, '--host', config.host, 'exec', '-i', pg,
      'pg_restore', '-U', 'proof', '-d', 'inbox_proof', '--exit-on-error', '--no-owner', '--no-acl'], {input: dump});
    assert.deepEqual(await snapshot('restored-before-start'), before);
    const beforeEvents = await control('/events');
    await docker('start', app); await ready();
    assert.deepEqual(await snapshot('restored-after-start'), before);
    assert.deepEqual(await control('/events'), beforeEvents);
    const current = await request(base, `/internal/loomai-managed-dependency/v1/lifecycle/status?challenge=${randomUUID()}`, {headers: managedHeaders});
    assert.equal(current.current.state, 'DISABLED'); assert.equal(current.current.operationKey, 'disable-local');
    assert.equal(current.current.activeWork, 1);
    assert.equal((await lifecycle('DISABLE', 'disable-local')).replay, true);
    // Re-enable only the synthetic restored instance to test persisted idempotency and decryption.
    await lifecycle('INITIALIZE', 'initialize-after-restore');
    assert.deepEqual(await dispatch(first), accepted);
    assert.equal((await reply(first.handoverId, 'First synthetic reply', 'reply-1')).outcome, 'MESSAGE_REPLAYED');
    assert.equal((await operator(retryPath, {idempotencyKey: 'retry-1'})).outcome, 'CALLBACK_RETRY_REPLAYED');
    const restoredDetail = await detail(first.handoverId);
    assert.equal(restoredDetail.consentedIntake.name, 'Synthetic User');
    check(restoredDetail.messages.some(row => row.content === 'First synthetic reply'), 'RESTORED_DECRYPTION_REQUIRED');
    await new Promise(done => setTimeout(done, 1800));
    assert.deepEqual(await control('/events'), beforeEvents);
    const final = await snapshot('restored-after-replays');
    for (const table of TABLES.filter(table => !table.startsWith('loomai_managed_dependency_'))) assert.deepEqual(final[table], before[table]);
    await save('callback-events.final.json', beforeEvents);
    await fact('QUIESCENT_PGDUMP_RESTORE_EXACT_ALL_TABLE_ROWS_NO_HISTORICAL_DELIVERY',
      {dumpSha256: sha(dump), dumpBytes: dump.length, tables: TABLES,
        callbackCount: before.inbox_callback_outbox.length, dispatchReceiptCount: before.inbox_dispatch_receipt.length,
        operatorReceiptCount: before.inbox_operator_operation.length,
        syntheticKeySha256: report.syntheticKeySha256});
    report.status = 'PASS';
  } catch (error) {
    report.status = 'FAIL';
    report.failures.push({at: new Date().toISOString(), message: error.message, stack: error.stack,
      response: error.response, last: error.last,
      stdout: error.result?.stdout?.toString(), stderr: error.result?.stderr?.toString()});
  } finally {
    cleaning = true;
    try { await save('requests.actual.json', report.requests); }
    catch (error) { report.failures.push({capture: 'requests', message: error.message}); report.status = 'FAIL'; }
    for (const item of resources.filter(value => value.type === 'container')) {
      try { await save(`${item.name}.log`, await command('docker', ['--config', dockerConfig, '--host', config.host,
        'logs', '--timestamps', item.id], {mergeStderr: true})); } catch (error) { report.failures.push({capture: item.name, message: error.message}); report.status = 'FAIL'; }
    }
    for (const item of [...resources].reverse()) {
      try {
        owned(await inspect(item.type, item.id), item.type, item.id, runId);
        if (item.type === 'container') await docker('rm', '-f', item.id);
        else await docker(item.type, 'rm', item.id);
        report.cleanup.push({...item, status: 'REMOVED_EXACT_OWNED'});
      } catch (error) { report.cleanup.push({...item, status: 'FAILED', message: error.message}); report.status = 'FAIL'; }
    }
    report.finishedAt = new Date().toISOString();
    await save('report.json', report);
    process.removeListener('SIGINT', interrupt); process.removeListener('SIGTERM', interrupt);
  }
  return report;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const result = await run(configuration(process.argv.slice(2)));
    console.log(JSON.stringify({status: result.status, root: process.argv[process.argv.indexOf('--output-root') + 1], facts: result.facts.length}));
    if (result.status !== 'PASS') process.exitCode = 1;
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
