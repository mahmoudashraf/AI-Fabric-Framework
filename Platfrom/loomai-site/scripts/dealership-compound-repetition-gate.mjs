import { spawn } from 'node:child_process'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const qualityScript = resolve(scriptDirectory, 'dealership-quality-check.mjs')
const origin = process.env.DEALERSHIP_QUALITY_ORIGIN || 'https://loomai.pro'
const repetitions = positiveInteger(process.env.DEALERSHIP_COMPOUND_REPETITIONS, 5)
const outputPath = resolve(
  process.env.DEALERSHIP_COMPOUND_OUTPUT
    || resolve(scriptDirectory, '../test-results/dealership-quality/compound-repetition.json'),
)
const runRoot = resolve(tmpdir(), `loomai-compound-repetition-${process.pid}`)
const forward = 'compound-inventory-policy-forward'
const reverse = 'compound-inventory-policy-reverse'
const orders = [
  { id: 'forward-first', scenarios: [forward, reverse] },
  { id: 'reverse-first', scenarios: [reverse, forward] },
]

const startedAt = new Date().toISOString()
const runs = []

try {
  await mkdir(runRoot, { recursive: true })
  for (const order of orders) {
    for (let repetition = 1; repetition <= repetitions; repetition += 1) {
      const runId = `${order.id}-${repetition}`
      const reportPath = resolve(runRoot, `${runId}.json`)
      process.stderr.write(`[compound-repetition] ${runId}\n`)
      const child = await runQualityGate({ scenarios: order.scenarios, reportPath })
      const report = JSON.parse(await readFile(reportPath, 'utf8'))
      const summarizedScenarios = report.scenarios?.map((scenario, index) => ({
        id: scenario.id,
        conversationScope: index === 0 ? 'FRESH' : 'CONTINUING',
        status: scenario.status,
        providerRequestId: scenario.response?.providerRequestId || null,
        conversationId: scenario.response?.conversationId || null,
        groundingPath: scenario.evidence?.groundingPath || null,
        executedActions: scenario.evidence?.executedActions || [],
        documentSourceIds: uniqueStrings(
          scenario.evidence?.externalDocuments?.map((document) => document.knowledgeSourceId) || [],
        ),
        compoundEvidence: scenario.evidence?.compoundEvidence || null,
        failedAssertions: scenario.assertions
          ?.filter((assertion) => assertion.passed !== true)
          .map((assertion) => assertion.name) || [],
      })) || []
      runs.push({
        runId,
        order: order.scenarios,
        status: child.exitCode === 0 && report.status === 'PASS' ? 'PASS' : 'FAIL',
        reportStatus: report.status,
        scenarios: summarizedScenarios,
        stderrTail: child.exitCode === 0 ? [] : tailLines(child.stderr, 20),
      })
    }
  }

  const coverage = [forward, reverse].map((scenarioId) => ({
    scenarioId,
    freshPasses: countPasses(runs, scenarioId, 'FRESH'),
    continuingPasses: countPasses(runs, scenarioId, 'CONTINUING'),
  }))
  const passed = runs.every((run) => run.status === 'PASS')
    && coverage.every((entry) => (
      entry.freshPasses === repetitions && entry.continuingPasses === repetitions
    ))
  const aggregate = {
    schemaVersion: 'loomai-dealership-compound-repetition-v1',
    status: passed ? 'PASS' : 'FAIL',
    target: {
      origin,
      route: process.env.DEALERSHIP_QUALITY_ROUTE || '/demos/dealership-ai',
    },
    run: {
      startedAt,
      completedAt: new Date().toISOString(),
      repetitions,
      totalRuns: runs.length,
      totalQueries: runs.reduce((total, run) => total + run.scenarios.length, 0),
    },
    coverage,
    runs,
  }
  await mkdir(dirname(outputPath), { recursive: true })
  await writeFile(outputPath, `${JSON.stringify(aggregate, null, 2)}\n`, 'utf8')
  process.stdout.write(`${JSON.stringify({ status: aggregate.status, run: aggregate.run, coverage }, null, 2)}\n`)
  if (!passed) process.exitCode = 1
} finally {
  await rm(runRoot, { recursive: true, force: true })
}

function runQualityGate({ scenarios, reportPath }) {
  return new Promise((resolveRun, rejectRun) => {
    const child = spawn(process.execPath, [qualityScript], {
      cwd: resolve(scriptDirectory, '..'),
      env: {
        ...process.env,
        DEALERSHIP_QUALITY_ORIGIN: origin,
        DEALERSHIP_QUALITY_STRICT: 'true',
        DEALERSHIP_QUALITY_SCENARIOS: scenarios.join(','),
        DEALERSHIP_QUALITY_OUTPUT: reportPath,
      },
      stdio: ['ignore', 'ignore', 'pipe'],
    })
    let stderr = ''
    child.stderr.setEncoding('utf8')
    child.stderr.on('data', (chunk) => {
      stderr = `${stderr}${chunk}`.slice(-20_000)
    })
    child.on('error', rejectRun)
    child.on('close', (exitCode) => resolveRun({ exitCode, stderr }))
  })
}

function countPasses(runs, scenarioId, conversationScope) {
  return runs.reduce((count, run) => count + run.scenarios.filter((scenario) => (
    scenario.id === scenarioId
      && scenario.conversationScope === conversationScope
      && scenario.status === 'PASS'
  )).length, 0)
}

function uniqueStrings(values) {
  return [...new Set(values.filter((value) => typeof value === 'string' && value.trim()).map((value) => value.trim()))]
}

function tailLines(value, count) {
  return String(value || '').trim().split(/\r?\n/).slice(-count)
}

function positiveInteger(value, fallback) {
  const parsed = Number.parseInt(String(value || ''), 10)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}
