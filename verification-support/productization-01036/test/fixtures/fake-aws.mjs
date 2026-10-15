#!/usr/bin/env node

import fs from 'node:fs';

const args = process.argv.slice(2);
const mode = process.env.FAKE_AWS_MODE || 'strict';
const accessKey = process.env.AWS_ACCESS_KEY_ID || '';
const scope = accessKey.includes('PRODUCTION') ? 'production' : 'staging';
const ownPrefix = `dealership/${scope}/`;
const foreignPrefix = scope === 'staging' ? 'dealership/production/' : 'dealership/staging/';
const valueAfter = (flag) => args[args.indexOf(flag) + 1];
const operation = args.find((value) => [
  'get-caller-identity', 'get-bucket-versioning', 'list-objects-v2', 'head-object',
  'get-object', 'put-object', 'delete-object',
].includes(value));

if (process.env.FAKE_AWS_LOG) {
  fs.appendFileSync(process.env.FAKE_AWS_LOG, `${JSON.stringify({
    scope,
    args,
    canaryEnvironmentVariableCount: Object.keys(process.env).filter((name) => name.startsWith('S3_CANARY_')).length,
    endpointOverrideCount: Object.keys(process.env)
      .filter((name) => name === 'AWS_ENDPOINT_URL' || name.startsWith('AWS_ENDPOINT_URL_')).length,
    effectiveRegion: process.env.AWS_REGION || null,
    effectiveDefaultRegion: process.env.AWS_DEFAULT_REGION || null,
  })}\n`);
}

if (args[0] === '--version') {
  process.stderr.write(`aws-cli/${process.env.FAKE_AWS_VERSION || '2.99.0'} fixture\n`);
  process.exit(0);
}

function deny(name = 'AccessDenied') {
  process.stderr.write(`An error occurred (${name}) when calling the ${operation} operation: fixture\n`);
  process.exit(254);
}

function notFound() {
  process.stderr.write(`An error occurred (NoSuchKey) when calling the ${operation} operation: fixture 404\n`);
  process.exit(254);
}

function output(value) {
  process.stdout.write(`${JSON.stringify(value)}\n`);
  process.exit(0);
}

if (args.includes('--generate-cli-skeleton')) {
  const skeleton = { Bucket: '', Key: '' };
  if (mode !== 'conditional-probe-unsupported') skeleton.IfMatch = '';
  output(skeleton);
}

if (operation === 'get-caller-identity') {
  const principalScope = mode === 'same-principal' ? 'shared' : scope;
  output({
    Account: principalScope === 'production' ? '222222222222' : '111111111111',
    Arn: `arn:aws:iam::fixture:user/${principalScope}`,
  });
}
if (operation === 'get-bucket-versioning') {
  output(mode === 'versioned' ? { Status: 'Enabled' } : {});
}

const key = valueAfter('--key');
const prefix = valueAfter('--prefix');
const crossListReadable = mode === 'cross-readable';
const crossHeadReadable = mode === 'cross-readable' || mode === 'cross-head-readable';
const crossGetReadable = mode === 'cross-readable' || mode === 'cross-get-readable';
const objectContent = (objectKey) => {
  if (mode === 'invalid-utf8' && objectKey?.startsWith(ownPrefix)) return Buffer.from([0xc3, 0x28]);
  const selectedPrefix = objectKey?.startsWith(foreignPrefix) ? foreignPrefix : ownPrefix;
  return Buffer.from(`Policy ${objectKey?.match(/policy-(\d+)/)?.[1] || '1'} for ${selectedPrefix}\n`);
};
if (operation === 'list-objects-v2') {
  if (prefix === foreignPrefix && !crossListReadable) deny();
  const selectedPrefix = prefix || ownPrefix;
  const objectCount = mode === 'insufficient-text' && selectedPrefix === ownPrefix ? 2 : 3;
  output({
    IsTruncated: false,
    Contents: Array.from({ length: objectCount }, (_, index) => {
      const objectKey = mode === 'listing-escape' && selectedPrefix === ownPrefix && index === 0
        ? `${foreignPrefix}policy-${index + 1}.txt`
        : `${selectedPrefix}policy-${index + 1}.txt`;
      return { Key: objectKey, Size: objectContent(objectKey).length };
    }),
  });
}
if (operation === 'head-object') {
  if (key?.includes('.loomai-permission-probe/')) notFound();
  if (key?.startsWith(foreignPrefix) && !crossHeadReadable) deny();
  const content = objectContent(key);
  output({
    ContentLength: content.length, ContentType: 'text/plain; charset=utf-8',
    ETag: `fixture-${scope}-${key?.slice(-12)}`, LastModified: '2026-10-14T10:00:00Z',
  });
}
if (operation === 'get-object') {
  if (key?.startsWith(foreignPrefix) && !crossGetReadable) deny();
  const destination = args[args.indexOf(key) + 1];
  const content = objectContent(key);
  fs.writeFileSync(destination, content);
  output({ ContentLength: content.length, ContentType: 'text/plain; charset=utf-8' });
}
if (operation === 'put-object') {
  if (mode === 'write-allowed') deny('PreconditionFailed');
  deny();
}
if (operation === 'delete-object') {
  if (mode === 'delete-allowed') output({});
  deny();
}

process.stderr.write('Unsupported fixture invocation.\n');
process.exit(2);
