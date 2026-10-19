import {createHash, randomUUID} from 'node:crypto';
import {spawn} from 'node:child_process';
import {chmod, mkdir, mkdtemp, readFile, readdir, writeFile} from 'node:fs/promises';
import {delimiter, dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

// Local synthetic rehearsal only: no configurable endpoint, credential, image or database.
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const SOURCE = 'd2b1a9792dc10ec801d3ce30c170763f1f2ab301';
const RUNTIME = 'ghcr.io/mahmoudashraf/ai-fabric-runtime@sha256:3809a6585ad19f25c0139d60cf1b9aa742b7831a6213fcec4bdfe6fb52e0b140';
const POSTGRES = 'postgres@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea';
const RUNTIME_IMAGE_ID = 'sha256:15a68ee33fe2bcc9d9234b76f2fea0ad10e6d3799fcad1e07dfd232eb000f564';
const POSTGRES_IMAGE_ID = 'sha256:81bd698b4594e751a3269e4dcd3e03a4a0ec0daf7b72e7aa1abd43cce9887542';
const IMAGE_IDS = new Set([RUNTIME_IMAGE_ID, POSTGRES_IMAGE_ID]);
const PREVIOUS_SOURCE = '600f136bf0becb9a473ff1e085bbc9896b4ef696';
const PREVIOUS_RUNTIME = 'ghcr.io/mahmoudashraf/ai-fabric-runtime@sha256:d3f744e6c68677b49d128a37a99c31b0717ebf87b1b6d8dd45adbe14030baa28';
const PREVIOUS_RUNTIME_IMAGE_ID = 'sha256:eef208985b497d519004dbe04a0250270ac0231916cbf100a94432c2c20817a7';
const TEST_TEMPLATE_SHA256 = '9e70c06e0ba626852889a78cccde7376ba07e7fe2e5a8ad8864b03bb7c8992f6';
const LABEL = 'pro.loomai.local-budget-restore';
const CLASS = 'com.ai.fabric.runtime.budget.RuntimeBudgetExportRestoreTest';
const TOOLING_POM = `<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>local.verification</groupId><artifactId>budget-restore-tooling</artifactId><version>1</version>
  <dependencies>
    <dependency><groupId>org.assertj</groupId><artifactId>assertj-core</artifactId><version>3.27.7</version>
      <exclusions><exclusion><groupId>net.bytebuddy</groupId><artifactId>byte-buddy</artifactId></exclusion></exclusions>
    </dependency>
    <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter-api</artifactId><version>6.0.3</version></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers</artifactId><version>2.0.5</version>
      <exclusions><exclusion><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-annotations</artifactId></exclusion></exclusions>
    </dependency>
  </dependencies>
</project>\n`;
function requireCheck(condition, code) { if (!condition) throw new Error(code); }
const sha256 = value => createHash('sha256').update(value).digest('hex');

async function main() {
  requireCheck(process.env.RUN_01036_LOCAL_BUDGET_RESTORE === 'true', 'EXPLICIT_LOCAL_RESTORE_OPT_IN_REQUIRED');
  requireCheck(process.argv.length === 2, 'NO_ARGUMENTS_SUPPORTED');
  process.umask(0o077);
  const directory = await mkdtemp('/private/tmp/01036-local-budget-restore-');
  const runId = randomUUID();
  const env = Object.fromEntries(['PATH', 'HOME', 'JAVA_HOME', 'LANG', 'LC_ALL', 'TMPDIR', 'USER', 'LOGNAME']
    .filter(key => process.env[key] !== undefined).map(key => [key, process.env[key]]));
  env.MAVEN_OPTS = '-XX:ActiveProcessorCount=1 -Xms64m -Xmx512m';
  let dockerPrefix, aborted = false, killActive = () => {};
  const report = {status: 'RUNNING', source: SOURCE, runtimeImage: RUNTIME, postgresImage: POSTGRES,
    runtimeImageId: RUNTIME_IMAGE_ID, postgresImageId: POSTGRES_IMAGE_ID, runId,
    previousPins: {source: PREVIOUS_SOURCE, runtimeImage: PREVIOUS_RUNTIME, runtimeImageId: PREVIOUS_RUNTIME_IMAGE_ID},
    verificationScope: 'LATEST_RUNTIME_P5_SUBSYSTEM_ONLY', p5Pass: false,
    directory, network: 'INTERNAL_DOCKER_NETWORK_NO_PUBLISHED_PORTS', providerCalls: 0, liveDatabaseConnections: 0,
    cleanup: 'PENDING', boundary: 'Latest-runtime P5 budget-store subsystem only; not HTTP/bootstrap, hosted or whole-topology recovery, stale-backup reconciliation, or P5 acceptance.'};
  const abort = () => { aborted = true; killActive(); };
  process.on('SIGINT', abort); process.on('SIGTERM', abort);
  const watchdog = setTimeout(abort, 300000);

  async function run(program, args, {cwd = ROOT, timeout = 15000, log, cleanup = false} = {}) {
    requireCheck(cleanup || !aborted, 'LOCAL_REHEARSAL_CANCELLED');
    return new Promise((accept, reject) => {
      const child = spawn(program, args, {cwd, env, detached: true, stdio: ['ignore', 'pipe', 'pipe']});
      let stdout = '', stderr = '', failure;
      const kill = () => { try { process.kill(-child.pid, 'SIGKILL'); } catch { child.kill('SIGKILL'); } };
      killActive = kill;
      const timer = setTimeout(() => { failure = 'LOCAL_COMMAND_TIMEOUT'; kill(); }, timeout);
      function output(kind, chunk) {
        if (kind === 'stdout') stdout += chunk; else stderr += chunk;
        if (stdout.length + stderr.length > 4 * 1024 * 1024) { failure = 'LOCAL_OUTPUT_LIMIT'; kill(); }
      }
      child.stdout.on('data', chunk => output('stdout', chunk));
      child.stderr.on('data', chunk => output('stderr', chunk));
      child.on('error', () => { failure = 'LOCAL_TOOL_UNAVAILABLE'; });
      child.on('close', async code => {
        clearTimeout(timer); killActive = () => {};
        try {
          if (log) await writeFile(resolve(directory, log), stdout + stderr, {mode: 0o600});
          if (failure || code !== 0) reject(new Error(failure ?? 'LOCAL_COMMAND_FAILED'));
          else accept(stdout.trim());
        } catch { reject(new Error('LOCAL_LOG_UNAVAILABLE')); }
      });
    });
  }
  const docker = (args, options) => run('docker', [...dockerPrefix, ...args], options);

  async function cleanupOwned() {
    if (!dockerPrefix) return;
    const owned = (await docker(['container', 'ls', '--all', '--quiet', '--no-trunc', '--filter', `label=${LABEL}=${runId}`],
      {cleanup: true})).split('\n').filter(Boolean);
    for (const id of owned) {
      requireCheck(/^[a-f0-9]{64}$/.test(id), 'OWNED_CONTAINER_ID_INVALID');
      const [item] = JSON.parse(await docker(['container', 'inspect', id], {cleanup: true}));
      requireCheck(item.Config?.Labels?.[LABEL] === runId && IMAGE_IDS.has(item.Image)
        && (item.HostConfig?.Binds ?? []).length === 0, 'OWNED_CONTAINER_CLEANUP_SCOPE_INVALID');
      await docker(['container', 'rm', '--force', '--volumes', id], {cleanup: true});
    }
    const networks = (await docker(['network', 'ls', '--quiet', '--no-trunc', '--filter', `label=${LABEL}=${runId}`],
      {cleanup: true})).split('\n').filter(Boolean);
    for (const id of networks) {
      const [item] = JSON.parse(await docker(['network', 'inspect', id], {cleanup: true}));
      requireCheck(item.Labels?.[LABEL] === runId && item.Internal === true, 'OWNED_NETWORK_CLEANUP_SCOPE_INVALID');
      await docker(['network', 'rm', id], {cleanup: true});
    }
    requireCheck(!(await docker(['container', 'ls', '--all', '--quiet', '--filter', `label=${LABEL}=${runId}`], {cleanup: true}))
      && !(await docker(['network', 'ls', '--quiet', '--filter', `label=${LABEL}=${runId}`], {cleanup: true})), 'OWNED_RESOURCES_REMAIN');
  }

  try {
    report.helperSha256 = sha256(await readFile(fileURLToPath(import.meta.url)));
    const templatePath = resolve(ROOT, 'verification-support/productization-01036/java/com/ai/fabric/runtime/budget/RuntimeBudgetExportRestoreTest.java');
    const template = await readFile(templatePath, 'utf8');
    requireCheck(sha256(template) === TEST_TEMPLATE_SHA256, 'EXACT_TEST_TEMPLATE_REQUIRED');
    let adapted = template;
    const pinSubstitutions = [[PREVIOUS_RUNTIME, RUNTIME], [PREVIOUS_RUNTIME_IMAGE_ID, RUNTIME_IMAGE_ID]];
    for (const [previous, current] of pinSubstitutions) {
      requireCheck(adapted.split(previous).length === 2, 'EXACT_TEST_PIN_OCCURRENCE_REQUIRED');
      adapted = adapted.replace(previous, current);
    }
    const testSource = resolve(directory, 'RuntimeBudgetExportRestoreTest.java');
    await writeFile(testSource, adapted, {mode: 0o600});
    report.harness = {templatePath, templateSha256: TEST_TEMPLATE_SHA256, compiledSourceSha256: sha256(adapted),
      pinSubstitutions, assertionsChanged: false, repositoryJavaModified: false};
    const contexts = JSON.parse(await run('docker', ['context', 'inspect']));
    const socket = contexts.length === 1 ? contexts[0].Endpoints?.docker?.Host : null;
    requireCheck(typeof socket === 'string' && /^unix:\/\/\/[^\r\n\0?#]+$/.test(socket), 'LOCAL_UNIX_DOCKER_CONTEXT_REQUIRED');
    dockerPrefix = ['--host', socket]; env.DOCKER_HOST = socket;
    const images = JSON.parse(await docker(['image', 'inspect', RUNTIME, POSTGRES]));
    requireCheck(images.length === 2 && images[0].Id === RUNTIME_IMAGE_ID && images[1].Id === POSTGRES_IMAGE_ID,
      'EXACT_CACHED_IMAGES_REQUIRED_NO_PULL');
    requireCheck(images[0].RepoDigests?.includes(RUNTIME) && images[1].RepoDigests?.includes(POSTGRES)
      && images[0].Config?.Env?.includes(`APP_BUILD_COMMIT=${SOURCE}`)
      && images[0].Config?.Labels?.['org.opencontainers.image.revision'] === SOURCE
      && Object.keys(images[0].Config?.Volumes ?? {}).length === 0, 'EXACT_RUNTIME_LINEAGE_REQUIRED');
    const reader = await docker(['container', 'create', '--pull=never', '--network=none', '--read-only',
      '--label', `${LABEL}=${runId}`, '--entrypoint=/bin/true', RUNTIME]);
    requireCheck(/^[a-f0-9]{64}$/.test(reader), 'OWNED_READER_ID_REQUIRED');
    await docker(['container', 'cp', `${reader}:/app/runtime.jar`, resolve(directory, 'runtime.jar')], {timeout: 60000});
    await chmod(resolve(directory, 'runtime.jar'), 0o600);
    await cleanupOwned();
    const extracted = resolve(directory, 'extracted'), classes = resolve(directory, 'classes');
    await mkdir(extracted); await mkdir(classes);
    await run('jar', ['--extract', '--file', resolve(directory, 'runtime.jar'), 'BOOT-INF/classes', 'BOOT-INF/lib'],
      {cwd: extracted, timeout: 30000});
    // Production dependencies come from the image, not the workspace's framework BOM.
    const libraries = resolve(extracted, 'BOOT-INF/lib');
    const imageDependencies = (await readdir(libraries)).filter(name => name.endsWith('.jar')).sort()
      .map(name => resolve(libraries, name));
    requireCheck(imageDependencies.length > 0, 'EXACT_IMAGE_DEPENDENCIES_REQUIRED');
    requireCheck(['byte-buddy-', 'jackson-annotations-'].every(name =>
      imageDependencies.some(path => path.includes(`/${name}`))), 'IMAGE_PROVIDED_TOOLING_DEPENDENCIES_REQUIRED');
    const pom = resolve(directory, 'tooling.pom.xml');
    await writeFile(pom, TOOLING_POM, {mode: 0o600});
    const classpathFile = resolve(directory, 'test.classpath');
    await run('mvn', ['-o', '-B', '--no-transfer-progress', '-f', pom,
      'org.apache.maven.plugins:maven-dependency-plugin:3.10.0:build-classpath', '-DincludeScope=test',
      `-Dmdep.outputFile=${classpathFile}`, '-l', resolve(directory, 'maven-classpath.log')], {timeout: 60000, log: 'maven-console.log'});
    const dependencies = (await readFile(classpathFile, 'utf8')).trim();
    requireCheck(dependencies.length > 0 && !/[\r\n\0]/.test(dependencies), 'LOCAL_TEST_CLASSPATH_REQUIRED');
    const compileClasspath = [resolve(extracted, 'BOOT-INF/classes'), ...imageDependencies, dependencies].join(delimiter);
    report.classpath = {source: 'EXACT_IMAGE_LIBRARIES_PLUS_OFFLINE_TEST_TOOLING', imageLibraries: imageDependencies.length,
      toolingPomSha256: sha256(TOOLING_POM), testClasspathSha256: sha256(dependencies)};
    await run('javac', ['--release', '21', '-proc:none', '-d', classes, '-cp', compileClasspath, testSource],
    {timeout: 30000, log: 'javac.log'});
    Object.assign(env, {RUN_01036_LOCAL_BUDGET_RESTORE: 'true', BUDGET_RESTORE_RUN_ID: runId,
      TESTCONTAINERS_RYUK_DISABLED: 'true', TESTCONTAINERS_CHECKS_DISABLE: 'true'});
    const result = await run('java', ['-XX:ActiveProcessorCount=1', '-Xms64m', '-Xmx512m', '-cp',
      [classes, compileClasspath].join(delimiter), CLASS], {timeout: 120000, log: 'restore.log'});
    requireCheck(result.includes('01036_LOCAL_RESTORE_PASS archiveSha256=')
      && result.includes('01036_LOCAL_RESTORE_CLEANUP_COMPLETE'), 'EXACT_RESTORE_ASSERTIONS_NOT_COMPLETE');
    const proof = result.match(/^01036_LOCAL_RESTORE_PASS archiveSha256=([a-f0-9]{64}) exactImage=true exactStore=true active=true exactApproval=true unknownHolds=true scopeIsolation=6 schema=19 providerCalls=0$/m);
    requireCheck(proof, 'EXACT_RESTORE_PROOF_REQUIRED');
    report.archiveSha256 = proof[1];
    report.assertions = {exactImage: true, exactStore: true, active: true, exactApproval: true,
      unknownHolds: true, scopeIsolation: 6, schema: 19, providerCalls: 0};
    report.status = 'PASS';
  } catch (error) {
    report.status = 'FAIL'; report.code = /^[A-Z_]+$/.test(error.message) ? error.message : 'LOCAL_REHEARSAL_UNAVAILABLE';
  } finally {
    clearTimeout(watchdog);
    try { await cleanupOwned(); report.cleanup = 'OWNED_RESOURCES_REMOVED'; }
    catch { report.status = 'FAIL'; report.cleanup = 'NOT_PROVEN'; }
    process.off('SIGINT', abort); process.off('SIGTERM', abort);
    await writeFile(resolve(directory, 'report.json'), JSON.stringify(report, null, 2) + '\n', {mode: 0o600});
    console.log(JSON.stringify(report, null, 2)); process.exitCode = report.status === 'PASS' ? 0 : 1;
  }
}
main().catch(error => {
  console.error(/^[A-Z_]+$/.test(error.message) ? error.message : 'LOCAL_RESTORE_PREFLIGHT_FAILED'); process.exitCode = 1;
});
