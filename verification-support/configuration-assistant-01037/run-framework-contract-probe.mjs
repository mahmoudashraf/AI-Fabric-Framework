import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const here = dirname(fileURLToPath(import.meta.url));
const archive = resolve(process.argv[2] ?? resolve(here,
  '../../ai-infrastructure-module/ai-fabric-runtime/target/ai-fabric-runtime-0.1.0-preview.jar'));
const temporary = mkdtempSync(resolve(tmpdir(), 'loomai-authoring-contract-'));
function run(command, args, options = {}) {
  const result = spawnSync(command, args, { encoding: 'utf8', ...options });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} failed (${result.status}):\n${result.stdout}\n${result.stderr}`);
  return result.stdout;
}

try {
  const entries = run('unzip', ['-Z', '-1', archive]).split('\n');
  const framework = entries.filter(entry => /^BOOT-INF\/lib\/ai-fabric-.*-0\./.test(entry));
  if (!framework.length || framework.some(entry => !entry.endsWith('-0.8.46.jar'))) {
    throw new Error('Use a packaged runtime containing only released AI Fabric 0.8.46 framework libraries.');
  }
  console.log(`Inspecting ${framework.length} packaged framework libraries at 0.8.46.`);
  run('unzip', ['-q', '-j', archive, 'BOOT-INF/lib/*', '-d', temporary]);
  const java = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin/java') : 'java';
  const javac = process.env.JAVA_HOME ? resolve(process.env.JAVA_HOME, 'bin/javac') : 'javac';
  run(javac, ['--release', '21', '-proc:none', '--class-path', `${temporary}/*`, '-d', temporary,
    resolve(here, 'FrameworkAuthoringContractProbe.java')]);
  console.log(run(java, ['--class-path', `${temporary}:${temporary}/*`,
    'ai.fabric.provider.springai.FrameworkAuthoringContractProbe']));
} finally {
  rmSync(temporary, { recursive: true, force: true });
}
