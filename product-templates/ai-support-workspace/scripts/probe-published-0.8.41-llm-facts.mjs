#!/usr/bin/env node
import { readFileSync, statSync } from 'node:fs';
import { delimiter, dirname, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const scripts = dirname(fileURLToPath(import.meta.url));
const usage = `Usage: node ${process.argv[1]} [--classpath-file <existing-maven-cp.txt>] [--diagnostic-proof <saved-safe-status-value.json>] [--json]

Or set RUNTIME_MAVEN_CLASSPATH to an existing runtime Maven classpath.
JAVA_HOME must point to Java 21 or newer. No Maven build or network calls are made.
The published handler/executor parse fixture connector responses through fake HTTP
before projecting facts. Manifest 1.0.3 is checked against 1.0.1 and wrong-root 1.0.2.
The Java 21 source launcher writes no .class files. Only existing JAR entries are used;
runtime target/classes and target/test-classes are excluded from the probe classpath.
Nonzero exit means a contract assertion failed or the probe could not run.`;

let classpath = process.env.RUNTIME_MAVEN_CLASSPATH;
let json = false;
let diagnosticProof;
for (let i = 2; i < process.argv.length; i++) {
  const arg = process.argv[i];
  if (arg === '--help' || arg === '-h') {
    process.stdout.write(`${usage}\n`);
    process.exit(0);
  } else if (arg === '--classpath-file' && process.argv[i + 1]) {
    classpath = readFileSync(resolve(process.argv[++i]), 'utf8').trim();
  } else if (arg === '--json') {
    json = true;
  } else if (arg === '--diagnostic-proof' && process.argv[i + 1]) {
    diagnosticProof = resolve(process.argv[++i]);
  } else {
    throw new Error(`Unknown or incomplete argument: ${arg}\n${usage}`);
  }
}
if (!classpath?.trim()) throw new Error(`Existing runtime Maven classpath is required.\n${usage}`);
const jars = [...new Set(classpath.trim().split(delimiter).filter(entry => entry.endsWith('.jar')))];
if (!jars.length) throw new Error('Classpath contains no explicit Maven JAR paths.');
for (const path of jars) {
  if (!statSync(path).isFile()) throw new Error(`Classpath entry is not an existing JAR: ${path}`);
}
for (const artifact of ['ai-fabric-core', 'ai-fabric-actions-connector']) {
  const matches = jars.filter(path => path.split(/[\\/]/).at(-1)?.startsWith(`${artifact}-`));
  if (matches.length !== 1 || !matches[0].endsWith(`/${artifact}-0.8.41.jar`)) {
    throw new Error(`Expected exactly one published ${artifact}-0.8.41.jar, got ${matches.join(', ')}`);
  }
}
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
const child = spawnSync(java, [
  '--source', '21', '--class-path', jars.join(delimiter),
  join(scripts, 'Published0841LlmFactsProbe.java'),
  resolve(scripts, '../manifests/loomai-support-diagnostics.1.0.3.manifest.json'),
  resolve(scripts, '../manifests/loomai-support-diagnostics.1.0.1.manifest.json'),
  resolve(scripts, '../manifests/loomai-support-diagnostics.1.0.2.manifest.json'),
  ...(diagnosticProof ? [diagnosticProof] : []),
], { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, timeout: 120_000 });
if (child.error) throw child.error;
if (child.stderr) process.stderr.write(child.stderr);
const marker = 'LLM_FACTS_PROBE_RESULT=';
const resultLine = child.stdout.split('\n').find(line => line.startsWith(marker));
if (!resultLine) {
  process.stderr.write(child.stdout);
  throw new Error(`Java probe produced no structured result (exit ${child.status}, signal ${child.signal}).`);
}
const report = JSON.parse(resultLine.slice(marker.length));
if (json) {
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
} else {
  process.stdout.write(`Published 0.8.41 LLM facts required contract: ${report.status}; ${report.passed} passed, ${report.failed} failed; ${report.knownLimitations.length} known SDK bounded-detail limitations\n`);
  process.stdout.write(`PATH ${report.executionPath}\nHTTP ${report.httpTransport}\n`);
  for (const check of report.checks) {
    process.stdout.write(`${check.status} ${check.name}${check.detail ? `\n  ${check.detail}` : ''}\n`);
  }
  for (const observation of report.observations) {
    process.stdout.write(`SIZE ${observation.fixture}: direct=${observation.directChars}, bounded=${observation.boundedChars}/${report.maxEvidenceChars}; parserUnwrapped=${observation.parserUnwrapped}, fakeHttpCalls=${observation.fakeHttpCalls}\n`);
  }
  for (const limitation of report.knownLimitations) {
    process.stdout.write(`LIMIT ${limitation.fixture}: ${limitation.detail} Fixture-only omitted blocker codes: ${JSON.stringify(limitation.fixtureOnlyOmittedBlockerCodes)}\n`);
  }
}
process.exitCode = child.status ?? 1;
