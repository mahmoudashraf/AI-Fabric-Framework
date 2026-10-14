#!/usr/bin/env node

import crypto from 'node:crypto';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = path.dirname(fileURLToPath(import.meta.url));
const EXPECTED = Object.freeze({
  bucket: 'loomai-dealership-canary-978e0cdb142540e2',
  region: 'eu-north-1',
  endpoint: 'https://s3.eu-north-1.amazonaws.com',
  versioningEnabled: false,
  stagingPrefix: 'dealership/staging/',
  productionPrefix: 'dealership/production/',
});
const MAX_OBJECTS = 100;
const MAX_OBJECT_BYTES = 1_048_576;

class VerificationError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'VerificationError';
    this.code = code;
  }
}

class AwsCommandError extends Error {
  constructor(operation, classification, status) {
    super(`${operation} failed (${classification}).`);
    this.name = 'AwsCommandError';
    this.operation = operation;
    this.classification = classification;
    this.status = status;
  }
}

function sha256(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

function parseArguments(argv) {
  const result = {};
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--env-file' || argument === '--output' || argument === '--aws-cli') {
      const value = argv[index + 1];
      if (!value || value.startsWith('--')) {
        throw new VerificationError('INVALID_ARGUMENTS', `${argument} requires a value.`);
      }
      result[argument.slice(2).replace('-', '')] = value;
      index += 1;
    } else if (argument === '--help') {
      result.help = true;
    } else {
      throw new VerificationError('INVALID_ARGUMENTS', 'An unknown command-line argument was provided.');
    }
  }
  return result;
}

function parseEnvFile(file) {
  if (!file) return {};
  const absolute = path.resolve(file);
  if (!fs.existsSync(absolute)) {
    throw new VerificationError('ENV_FILE_NOT_FOUND', 'The requested private environment file was not found.');
  }
  const fileStatus = fs.lstatSync(absolute);
  if (!fileStatus.isFile() || fileStatus.isSymbolicLink()) {
    throw new VerificationError(
      'ENV_FILE_NOT_PRIVATE',
      'The private environment path must be a regular file and must not be a symbolic link.',
    );
  }
  if (process.platform !== 'win32' && (fileStatus.mode & 0o077) !== 0) {
    throw new VerificationError(
      'ENV_FILE_NOT_PRIVATE',
      'The private environment file must not be readable, writable, or executable by group or other users.',
    );
  }
  const values = {};
  for (const rawLine of fs.readFileSync(absolute, 'utf8').split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const match = line.match(/^(?:export\s+)?(S3_CANARY_[A-Z0-9_]+)=(.*)$/);
    if (!match) continue;
    let value = match[2].trim();
    if ((value.startsWith('"') && value.endsWith('"'))
      || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    values[match[1]] = value;
  }
  return values;
}

function required(values, name) {
  const value = values[name]?.trim();
  if (!value || /^<.*>$/.test(value) || /pending/i.test(value)) {
    throw new VerificationError('MISSING_CONFIGURATION', `${name} is required.`);
  }
  return value;
}

function normalizedEndpoint(value) {
  let parsed;
  try {
    parsed = new URL(value);
  } catch {
    throw new VerificationError('INVALID_ENDPOINT', 'S3_CANARY_ENDPOINT must be an absolute HTTPS origin.');
  }
  if (parsed.protocol !== 'https:' || parsed.username || parsed.password
    || parsed.search || parsed.hash || !['', '/'].includes(parsed.pathname)) {
    throw new VerificationError('INVALID_ENDPOINT', 'S3_CANARY_ENDPOINT must be an absolute HTTPS origin.');
  }
  return parsed.origin;
}

function readConfiguration(fileValues) {
  const values = { ...fileValues, ...Object.fromEntries(
    Object.entries(process.env).filter(([name, value]) => name.startsWith('S3_CANARY_') && value !== undefined),
  ) };
  const versioningValue = required(values, 'S3_CANARY_VERSIONING_ENABLED').toLowerCase();
  if (!['true', 'false'].includes(versioningValue)) {
    throw new VerificationError('INVALID_CONFIGURATION', 'S3_CANARY_VERSIONING_ENABLED must be true or false.');
  }
  const config = {
    bucket: required(values, 'S3_CANARY_BUCKET'),
    region: required(values, 'S3_CANARY_REGION'),
    endpoint: normalizedEndpoint(required(values, 'S3_CANARY_ENDPOINT')),
    versioningEnabled: versioningValue === 'true',
    scopes: {
      staging: {
        prefix: required(values, 'S3_CANARY_STAGING_PREFIX'),
        accessKeyId: required(values, 'S3_CANARY_STAGING_ACCESS_KEY_ID'),
        secretAccessKey: required(values, 'S3_CANARY_STAGING_SECRET_ACCESS_KEY'),
        sessionToken: values.S3_CANARY_STAGING_SESSION_TOKEN?.trim() || '',
      },
      production: {
        prefix: required(values, 'S3_CANARY_PRODUCTION_PREFIX'),
        accessKeyId: required(values, 'S3_CANARY_PRODUCTION_ACCESS_KEY_ID'),
        secretAccessKey: required(values, 'S3_CANARY_PRODUCTION_SECRET_ACCESS_KEY'),
        sessionToken: values.S3_CANARY_PRODUCTION_SESSION_TOKEN?.trim() || '',
      },
    },
  };
  assertExactContract(config);
  return config;
}

function assertExactContract(config) {
  for (const field of ['bucket', 'region', 'endpoint', 'versioningEnabled']) {
    if (config[field] !== EXPECTED[field]) {
      throw new VerificationError('CONTRACT_MISMATCH', `${field} does not match the reviewed 010.36 canary contract.`);
    }
  }
  if (config.scopes.staging.prefix !== EXPECTED.stagingPrefix
    || config.scopes.production.prefix !== EXPECTED.productionPrefix) {
    throw new VerificationError('CONTRACT_MISMATCH', 'The staging or production prefix does not match the reviewed contract.');
  }
  if (config.scopes.staging.accessKeyId === config.scopes.production.accessKeyId) {
    throw new VerificationError('IDENTITIES_NOT_SEPARATE', 'Staging and production must use separate access keys.');
  }
}

function credentialEnvironment(scope) {
  const environment = { ...process.env };
  for (const name of Object.keys(environment)) {
    if (name.startsWith('S3_CANARY_')) delete environment[name];
  }
  for (const name of [
    'AWS_PROFILE', 'AWS_DEFAULT_PROFILE', 'AWS_CONFIG_FILE', 'AWS_SHARED_CREDENTIALS_FILE',
    'AWS_ROLE_ARN', 'AWS_WEB_IDENTITY_TOKEN_FILE', 'AWS_CONTAINER_CREDENTIALS_RELATIVE_URI',
    'AWS_CONTAINER_CREDENTIALS_FULL_URI', 'AWS_CONTAINER_AUTHORIZATION_TOKEN', 'AWS_SECURITY_TOKEN',
  ]) delete environment[name];
  environment.AWS_ACCESS_KEY_ID = scope.accessKeyId;
  environment.AWS_SECRET_ACCESS_KEY = scope.secretAccessKey;
  if (scope.sessionToken) environment.AWS_SESSION_TOKEN = scope.sessionToken;
  else delete environment.AWS_SESSION_TOKEN;
  environment.AWS_EC2_METADATA_DISABLED = 'true';
  environment.AWS_PAGER = '';
  environment.AWS_CLI_AUTO_PROMPT = 'off';
  return environment;
}

function prerequisiteEnvironment() {
  const environment = { ...process.env, AWS_PAGER: '', AWS_CLI_AUTO_PROMPT: 'off' };
  for (const name of Object.keys(environment)) {
    if (name.startsWith('S3_CANARY_')) delete environment[name];
  }
  for (const name of [
    'AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_SESSION_TOKEN', 'AWS_SECURITY_TOKEN',
    'AWS_PROFILE', 'AWS_DEFAULT_PROFILE', 'AWS_CONFIG_FILE', 'AWS_SHARED_CREDENTIALS_FILE',
    'AWS_ROLE_ARN', 'AWS_WEB_IDENTITY_TOKEN_FILE', 'AWS_CONTAINER_CREDENTIALS_RELATIVE_URI',
    'AWS_CONTAINER_CREDENTIALS_FULL_URI', 'AWS_CONTAINER_AUTHORIZATION_TOKEN',
  ]) delete environment[name];
  environment.AWS_EC2_METADATA_DISABLED = 'true';
  return environment;
}

function classifyAwsError(stderr) {
  const value = String(stderr || '');
  if (/AccessDenied|Forbidden|status code:\s*403|\b403\b/i.test(value)) return 'ACCESS_DENIED';
  if (/NoSuchKey|Not Found|NotFound|status code:\s*404|\b404\b/i.test(value)) return 'NOT_FOUND';
  if (/PreconditionFailed|status code:\s*412|\b412\b/i.test(value)) return 'PRECONDITION_FAILED';
  if (/ExpiredToken|InvalidAccessKeyId|SignatureDoesNotMatch|InvalidToken/i.test(value)) return 'AUTHENTICATION_FAILED';
  if (/PermanentRedirect|AuthorizationHeaderMalformed|IllegalLocationConstraint/i.test(value)) return 'REGION_OR_ENDPOINT_MISMATCH';
  return 'AWS_COMMAND_FAILED';
}

function invokeAws(context, scope, operation, service, args, options = {}) {
  const globalArgs = ['--no-cli-pager', '--no-paginate', '--region', context.config.region];
  if (service === 's3api') globalArgs.push('--endpoint-url', context.config.endpoint);
  const result = spawnSync(context.awsCli, [...globalArgs, service, ...args, '--output', 'json'], {
    encoding: 'utf8',
    env: credentialEnvironment(scope),
    maxBuffer: 8 * 1024 * 1024,
  });
  if (result.error) {
    throw new VerificationError('AWS_CLI_UNAVAILABLE', 'AWS CLI could not be executed.');
  }
  if (result.status !== 0) {
    const error = new AwsCommandError(operation, classifyAwsError(result.stderr), result.status);
    if (options.allowFailure) return { error };
    throw error;
  }
  try {
    return { value: result.stdout.trim() ? JSON.parse(result.stdout) : {} };
  } catch {
    throw new VerificationError('INVALID_AWS_RESPONSE', `${operation} returned invalid JSON.`);
  }
}

function assertAwsDenied(result, code, operation) {
  if (!result.error || result.error.classification !== 'ACCESS_DENIED') {
    throw new VerificationError(code, `${operation} was not explicitly denied.`);
  }
}

function checkAwsCli(awsCli) {
  const environment = prerequisiteEnvironment();
  const result = spawnSync(awsCli, ['--version'], { encoding: 'utf8', env: environment });
  if (result.error || result.status !== 0) {
    throw new VerificationError('AWS_CLI_UNAVAILABLE', 'AWS CLI is required and was not found or could not run.');
  }
  const versionText = `${result.stdout || ''} ${result.stderr || ''}`;
  const match = versionText.match(/aws-cli\/([0-9][^\s]*)/);
  if (!match || Number.parseInt(match[1].split('.')[0], 10) !== 2) {
    throw new VerificationError('AWS_CLI_VERSION_UNSUPPORTED', 'AWS CLI v2 is required for safe conditional permission probes.');
  }

  const supportsIfMatch = (command) => {
    const skeleton = spawnSync(awsCli, ['s3api', command, '--generate-cli-skeleton', 'input'], {
      encoding: 'utf8', env: environment, maxBuffer: 1024 * 1024,
    });
    if (skeleton.error || skeleton.status !== 0) {
      throw new VerificationError(
        'AWS_CLI_CONDITIONAL_PROBE_UNAVAILABLE',
        `AWS CLI cannot describe the safe conditional ${command} probe.`,
      );
    }
    try {
      const input = JSON.parse(skeleton.stdout);
      return Object.prototype.hasOwnProperty.call(input, 'IfMatch');
    } catch {
      throw new VerificationError(
        'AWS_CLI_CONDITIONAL_PROBE_UNAVAILABLE',
        `AWS CLI returned an invalid ${command} input skeleton.`,
      );
    }
  };
  if (!supportsIfMatch('put-object') || !supportsIfMatch('delete-object')) {
    throw new VerificationError(
      'AWS_CLI_CONDITIONAL_PROBE_UNAVAILABLE',
      'AWS CLI must support If-Match for both put-object and delete-object before permission probes can run.',
    );
  }
  return {
    version: match[1],
    conditionalPutIfMatch: true,
    conditionalDeleteIfMatch: true,
  };
}

function callerIdentity(context, scope) {
  const response = invokeAws(context, scope, 'caller identity', 'sts', ['get-caller-identity']).value;
  if (!response.Account || !response.Arn) {
    throw new VerificationError('INVALID_CALLER_IDENTITY', 'AWS caller identity was incomplete.');
  }
  return sha256(`${response.Account}|${response.Arn}`);
}

function bucketVersioning(context, scope) {
  const response = invokeAws(context, scope, 'bucket versioning', 's3api', [
    'get-bucket-versioning', '--bucket', context.config.bucket,
  ]).value;
  if (response.Status) {
    throw new VerificationError('VERSIONING_NOT_DISABLED', 'S3 bucket versioning must never have been enabled for this canary.');
  }
  return { expectedEnabled: false, observedStatus: 'UNVERSIONED' };
}

function listObjects(context, scope, prefix, operation = 'list own prefix') {
  const objects = [];
  let continuationToken = '';
  do {
    const args = [
      'list-objects-v2', '--bucket', context.config.bucket, '--prefix', prefix,
      '--max-keys', String(MAX_OBJECTS + 1),
    ];
    if (continuationToken) args.push('--continuation-token', continuationToken);
    const response = invokeAws(context, scope, operation, 's3api', args).value;
    objects.push(...(Array.isArray(response.Contents) ? response.Contents : []));
    if (objects.length > MAX_OBJECTS) {
      throw new VerificationError('TOO_MANY_OBJECTS', `The ${operation} scope exceeds the bounded ${MAX_OBJECTS}-object canary.`);
    }
    continuationToken = response.IsTruncated ? response.NextContinuationToken : '';
    if (response.IsTruncated && !continuationToken) {
      throw new VerificationError('INVALID_AWS_RESPONSE', `${operation} omitted its continuation token.`);
    }
  } while (continuationToken);
  return objects;
}

function headObject(context, scope, key, operation = 'head object', allowFailure = false) {
  return invokeAws(context, scope, operation, 's3api', [
    'head-object', '--bucket', context.config.bucket, '--key', key,
  ], { allowFailure });
}

function getObject(context, scope, key, destination, operation = 'get object', allowFailure = false) {
  return invokeAws(context, scope, operation, 's3api', [
    'get-object', '--bucket', context.config.bucket, '--key', key, destination,
  ], { allowFailure });
}

function metadataFingerprint(response) {
  return sha256(JSON.stringify({
    contentLength: response.ContentLength ?? null,
    contentType: response.ContentType ?? null,
    etag: response.ETag ?? null,
    lastModified: response.LastModified ?? null,
    versionId: response.VersionId ?? null,
  }));
}

function validateUtf8(buffer) {
  let text;
  try {
    text = new TextDecoder('utf-8', { fatal: true }).decode(buffer);
  } catch {
    throw new VerificationError('INVALID_UTF8_DOCUMENT', 'A canary .txt object is not valid UTF-8.');
  }
  if (!text.trim() || text.includes('\u0000')) {
    throw new VerificationError('INVALID_TEXT_DOCUMENT', 'A canary .txt object is empty or contains NUL bytes.');
  }
}

function inspectOwnScope(context, scopeName, scope) {
  const listed = listObjects(context, scope, scope.prefix);
  if (listed.some((object) => typeof object.Key !== 'string' || !object.Key.startsWith(scope.prefix))) {
    throw new VerificationError('PREFIX_SCOPE_VIOLATION', `${scopeName} listing returned an object outside its exact prefix.`);
  }
  const candidates = listed.filter((object) => typeof object.Key === 'string'
    && object.Key.startsWith(scope.prefix)
    && object.Key !== scope.prefix
    && object.Key.toLowerCase().endsWith('.txt'));
  if (candidates.length < 3) {
    throw new VerificationError('INSUFFICIENT_TEXT_OBJECTS', `${scopeName} must expose at least three UTF-8 .txt objects.`);
  }

  const inspected = [];
  for (const candidate of candidates) {
    const listedSize = Number(candidate.Size);
    if (!Number.isSafeInteger(listedSize) || listedSize <= 0 || listedSize > MAX_OBJECT_BYTES) {
      throw new VerificationError('OBJECT_SIZE_OUT_OF_BOUNDS', `${scopeName} contains a .txt object outside the canary size boundary.`);
    }
    const head = headObject(context, scope, candidate.Key).value;
    const headSize = Number(head.ContentLength);
    if (head.VersionId || headSize !== listedSize || headSize <= 0 || headSize > MAX_OBJECT_BYTES) {
      throw new VerificationError('OBJECT_METADATA_MISMATCH', `${scopeName} object metadata is inconsistent with the unversioned canary contract.`);
    }
    const destination = path.join(context.tempDirectory, `${scopeName}-${inspected.length}.txt`);
    getObject(context, scope, candidate.Key, destination);
    const content = fs.readFileSync(destination);
    if (content.length !== headSize) {
      throw new VerificationError('OBJECT_READ_INCOMPLETE', `${scopeName} object length changed while it was read.`);
    }
    validateUtf8(content);
    inspected.push({
      objectKeyFingerprint: sha256(candidate.Key),
      metadataFingerprint: metadataFingerprint(head),
      contentSha256: sha256(content),
      bytes: content.length,
      mediaType: head.ContentType || null,
    });
  }
  inspected.sort((left, right) => left.objectKeyFingerprint.localeCompare(right.objectKeyFingerprint));
  return {
    prefixFingerprint: sha256(scope.prefix),
    listedObjectCount: listed.length,
    utf8TextObjectCount: inspected.length,
    manifestFingerprint: sha256(JSON.stringify(inspected)),
    objects: inspected,
    firstKey: candidates[0].Key,
  };
}

function verifyCrossPrefixDenial(context, sourceName, sourceScope, foreignPrefix, foreignKey) {
  const listResult = invokeAws(context, sourceScope, 'cross-prefix list', 's3api', [
    'list-objects-v2', '--bucket', context.config.bucket, '--prefix', foreignPrefix, '--max-keys', '1',
  ], { allowFailure: true });
  assertAwsDenied(listResult, 'CROSS_PREFIX_LIST_NOT_DENIED', `${sourceName} cross-prefix list`);

  const headResult = headObject(context, sourceScope, foreignKey, 'cross-prefix head', true);
  assertAwsDenied(headResult, 'CROSS_PREFIX_HEAD_NOT_DENIED', `${sourceName} cross-prefix head`);

  const destination = path.join(context.tempDirectory, `${sourceName}-foreign-object`);
  const getResult = getObject(context, sourceScope, foreignKey, destination, 'cross-prefix get', true);
  assertAwsDenied(getResult, 'CROSS_PREFIX_GET_NOT_DENIED', `${sourceName} cross-prefix get`);
  if (fs.existsSync(destination)) {
    throw new VerificationError('CROSS_PREFIX_DATA_EXPOSED', `${sourceName} cross-prefix get created a local object.`);
  }
  return { list: 'ACCESS_DENIED', head: 'ACCESS_DENIED', get: 'ACCESS_DENIED' };
}

function verifyWriteDeleteDenial(context, scopeName, scope, existingKey) {
  const before = headObject(context, scope, existingKey).value;
  const probeBody = path.join(context.tempDirectory, `${scopeName}-write-probe.txt`);
  fs.writeFileSync(probeBody, 'LoomAI permission probe; this content must never be stored.\n', { mode: 0o600 });
  const impossibleEtag = `"loomai-permission-probe-${crypto.randomUUID()}"`;
  const putResult = invokeAws(context, scope, 'conditional put denial probe', 's3api', [
    'put-object', '--bucket', context.config.bucket, '--key', existingKey,
    '--body', probeBody, '--if-match', impossibleEtag,
  ], { allowFailure: true });
  assertAwsDenied(putResult, 'WRITE_NOT_DENIED', `${scopeName} conditional put`);
  const deleteResult = invokeAws(context, scope, 'conditional delete denial probe', 's3api', [
    'delete-object', '--bucket', context.config.bucket, '--key', existingKey,
    '--if-match', impossibleEtag,
  ], { allowFailure: true });
  assertAwsDenied(deleteResult, 'DELETE_NOT_DENIED', `${scopeName} conditional delete`);
  const after = headObject(context, scope, existingKey).value;
  if (metadataFingerprint(before) !== metadataFingerprint(after)) {
    throw new VerificationError('MUTATION_PROBE_CHANGED_OBJECT', `${scopeName} permission probes changed the protected object.`);
  }
  return { put: 'ACCESS_DENIED', delete: 'ACCESS_DENIED', objectUnchanged: true };
}

function outputPath(argument) {
  if (argument) return path.resolve(argument);
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  return path.join(directory, 'evidence', `s3-canary-${stamp}.json`);
}

function writeEvidence(file, evidence) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify(evidence, null, 2)}\n`, { mode: 0o600, flag: 'wx' });
}

function publicContract(config) {
  return {
    bucketFingerprint: sha256(config.bucket),
    region: config.region,
    endpoint: config.endpoint,
    pathStyleAccess: false,
    objectVersioningAvailable: false,
    stagingPrefixFingerprint: sha256(config.scopes.staging.prefix),
    productionPrefixFingerprint: sha256(config.scopes.production.prefix),
  };
}

async function verify(config, awsCli, awsCliPrerequisites) {
  const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'loomai-01036-s3-'));
  const context = { config, awsCli, tempDirectory };
  try {
    const identities = {
      staging: callerIdentity(context, config.scopes.staging),
      production: callerIdentity(context, config.scopes.production),
    };
    if (identities.staging === identities.production) {
      throw new VerificationError('IDENTITIES_NOT_SEPARATE', 'Staging and production resolved to the same AWS principal.');
    }
    const versioning = bucketVersioning(context, config.scopes.staging);
    const productionVersioning = bucketVersioning(context, config.scopes.production);
    if (JSON.stringify(versioning) !== JSON.stringify(productionVersioning)) {
      throw new VerificationError('VERSIONING_OBSERVATION_MISMATCH', 'The identities observed inconsistent versioning state.');
    }

    const staging = inspectOwnScope(context, 'staging', config.scopes.staging);
    const production = inspectOwnScope(context, 'production', config.scopes.production);
    const stagingCrossPrefix = verifyCrossPrefixDenial(
      context, 'staging', config.scopes.staging, config.scopes.production.prefix, production.firstKey,
    );
    const productionCrossPrefix = verifyCrossPrefixDenial(
      context, 'production', config.scopes.production, config.scopes.staging.prefix, staging.firstKey,
    );
    const stagingMutations = verifyWriteDeleteDenial(context, 'staging', config.scopes.staging, staging.firstKey);
    const productionMutations = verifyWriteDeleteDenial(context, 'production', config.scopes.production, production.firstKey);
    delete staging.firstKey;
    delete production.firstKey;

    return {
      schemaVersion: 1,
      generatedAt: new Date().toISOString(),
      status: 'PASS',
      evidenceLevel: 'LIVE_AWS_READ_AND_NON_MUTATING_PERMISSION_PROBES',
      tool: { name: 'loomai-01036-s3-canary', awsCli: awsCliPrerequisites },
      contract: publicContract(config),
      versioning,
      identitySeparation: {
        stagingPrincipalFingerprint: identities.staging,
        productionPrincipalFingerprint: identities.production,
        separate: true,
      },
      scopes: {
        staging: { ...staging, crossPrefix: stagingCrossPrefix, mutationDenial: stagingMutations },
        production: { ...production, crossPrefix: productionCrossPrefix, mutationDenial: productionMutations },
      },
    };
  } finally {
    fs.rmSync(tempDirectory, { recursive: true, force: true });
  }
}

function failureEvidence(config, error) {
  return {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    status: 'FAIL',
    evidenceLevel: 'CANARY_REJECTED',
    contract: config ? publicContract(config) : null,
    failure: {
      code: error instanceof VerificationError ? error.code : 'UNEXPECTED_FAILURE',
      message: error instanceof VerificationError ? error.message : 'Unexpected verifier failure.',
    },
  };
}

function help() {
  return `Usage: node verify-s3-canary.mjs [--env-file PATH] [--output PATH] [--aws-cli PATH]\n\n`
    + 'Real credentials must be supplied through the environment or an ignored env file.\n';
}

async function main() {
  let args;
  let config;
  let evidenceFile;
  try {
    args = parseArguments(process.argv.slice(2));
    if (args.help) {
      process.stdout.write(help());
      return;
    }
    config = readConfiguration(parseEnvFile(args.envfile));
    evidenceFile = outputPath(args.output);
    const awsCli = args.awscli || process.env.S3_CANARY_AWS_CLI || 'aws';
    const awsCliPrerequisites = checkAwsCli(awsCli);
    const evidence = await verify(config, awsCli, awsCliPrerequisites);
    writeEvidence(evidenceFile, evidence);
    process.stdout.write(`S3 canary PASS. Sanitized evidence: ${evidenceFile}\n`);
  } catch (error) {
    const safeError = error instanceof VerificationError
      ? error
      : error instanceof AwsCommandError
        ? new VerificationError(error.classification, `${error.operation} failed.`)
        : new VerificationError('UNEXPECTED_FAILURE', 'Unexpected verifier failure.');
    try {
      evidenceFile ||= outputPath(args?.output);
      writeEvidence(evidenceFile, failureEvidence(config, safeError));
      process.stderr.write(`S3 canary FAIL [${safeError.code}]. Sanitized evidence: ${evidenceFile}\n`);
    } catch {
      process.stderr.write(`S3 canary FAIL [${safeError.code}]. No evidence file was written.\n`);
    }
    process.exitCode = 1;
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main();
}

export { EXPECTED, VerificationError, parseEnvFile, readConfiguration, verify };
