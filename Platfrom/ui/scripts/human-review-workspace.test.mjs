import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

const source = await readFile(new URL('../src/pages/HumanReviewPage.tsx', import.meta.url), 'utf8')
const ast = ts.createSourceFile('HumanReviewPage.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
const names = ['selectReviewTask', 'filterReviewTasks', 'currentReviewTask', 'isPendingReviewTask', 'canDecideReviewTask', 'freezeReviewDecision', 'canConfirmReviewDecision']
const functions = ast.statements.filter(node => ts.isFunctionDeclaration(node) && names.includes(node.name?.text))
assert.equal(functions.length, names.length)
const compiled = ts.transpileModule(`${functions.map(node => node.getText(ast)).join('\n')}\nObject.assign(exports, { ${names.join(',')} });`, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText
const helpers = {}
new Function('exports', compiled)(helpers)
const { selectReviewTask, filterReviewTasks, currentReviewTask, canDecideReviewTask, freezeReviewDecision, canConfirmReviewDecision } = helpers

function task(overrides = {}) {
  return { taskId: 'task/a & b', policyId: { name: 'customer-operations', version: '1.2' }, type: 'ACTION_PROPOSAL',
    title: 'Review appointment', summary: 'A customer requested an appointment.', allowedDecisions: ['APPROVE', 'REJECT'],
    status: 'PENDING', createdAt: '2026-11-08T10:00:00Z', expiresAt: '2099-11-08T10:30:00Z', version: 4, ...overrides }
}
const scope = { deploymentId: 'brain-a', targetProfileId: 'target-staging', releaseId: 'release-a', reviewer: '["customer-a","actor-a"]' }

test('workspace has no implicit selection; default view still initially selects the first task', () => {
  const first = task()
  assert.equal(selectReviewTask([first], '', false), null)
  assert.equal(selectReviewTask([first], '', true), first)
  assert.equal(selectReviewTask([], '', true), null)
})

test('an explicit task ID is exact, including absent and foreign IDs, without first-row fallback', () => {
  const first = task()
  const second = task({ taskId: 'task-b' })
  assert.equal(selectReviewTask([first, second], second.taskId, false), second)
  assert.equal(selectReviewTask([first, second], first.taskId, false), first)
  for (const auto of [true, false]) {
    assert.equal(selectReviewTask([first, second], 'foreign-task', auto), null)
    assert.equal(selectReviewTask([first, second], 'TASK-B', auto), null)
    assert.equal(selectReviewTask([], first.taskId, auto), null)
  }
})

test('filters/search return only actual matching tasks without manufacturing history or mutating data', () => {
  const rows = [task(), task({ taskId: 'task-b', status: 'COMPLETED', title: 'Delivery', summary: 'Collected order' })]
  const original = structuredClone(rows)
  assert.deepEqual(filterReviewTasks(rows, 'COMPLETED', '  COLLECTED  '), [rows[1]])
  assert.deepEqual(filterReviewTasks(rows, 'PENDING', 'delivery'), [])
  assert.deepEqual(filterReviewTasks(rows, 'FAILED', ''), [])
  assert.deepEqual(filterReviewTasks([], '', 'appointment'), [])
  assert.deepEqual(filterReviewTasks(rows, '', 'task/a & b'), [rows[0]])
  assert.deepEqual(filterReviewTasks(rows, '', 'customer-operations'), rows)
  assert.deepEqual(filterReviewTasks(rows, '', '1.2'), rows)
  assert.deepEqual(filterReviewTasks(rows, '', 'action_proposal'), rows)
  assert.deepEqual(rows, original)
  assert.equal(filterReviewTasks(rows, '', 'delivery')[0], rows[1])
})

test('filtering does not redirect an already selected detail to another visible row', () => {
  const rows = [task(), task({ taskId: 'task-b', status: 'REJECTED' })]
  assert.deepEqual(filterReviewTasks(rows, 'REJECTED', ''), [rows[1]])
  assert.equal(selectReviewTask(rows, rows[0].taskId, false), rows[0])
})

test('detail identity and version must match the selected task; newer detail remains authoritative', () => {
  const inbox = task()
  const newer = task({ version: 5, allowedDecisions: ['REJECT'] })
  assert.equal(currentReviewTask(inbox.taskId, inbox, newer), newer)
  assert.equal(currentReviewTask(inbox.taskId, inbox, inbox), inbox)
  assert.equal(currentReviewTask(inbox.taskId, inbox, task({ version: 3 })), null)
  assert.equal(currentReviewTask(inbox.taskId, inbox, task({ taskId: 'foreign' })), null)
  assert.equal(currentReviewTask('', null, inbox), null)
  assert.equal(currentReviewTask(inbox.taskId, inbox, undefined), null)
})

test('exact detail links can display a returned terminal task without adding it to the inbox', () => {
  const completed = task({ status: 'APPROVED', version: 5 })
  const inbox = [task({ taskId: 'other-task' })]
  assert.equal(selectReviewTask(inbox, completed.taskId, false), null)
  assert.equal(currentReviewTask(completed.taskId, null, completed), completed)
  assert.deepEqual(filterReviewTasks(inbox, 'APPROVED', ''), [])
  assert.equal(currentReviewTask('foreign', null, completed), null)
})

test('confirmation freezes task, policy, allowed decisions, exact scope and idempotent decision ID', () => {
  const original = task()
  const context = { ...scope }
  const pending = freezeReviewDecision(original, 'APPROVE', context, 'decision-a')
  original.taskId = 'task-b'
  original.version = 99
  original.policyId.version = 'new-policy'
  original.allowedDecisions.length = 0
  context.targetProfileId = 'production'
  assert.equal(pending.task.taskId, 'task/a & b')
  assert.equal(pending.task.version, 4)
  assert.equal(pending.task.policyId.version, '1.2')
  assert.deepEqual(pending.task.allowedDecisions, ['APPROVE', 'REJECT'])
  assert.equal(pending.targetProfileId, scope.targetProfileId)
  assert.equal(pending.decisionId, 'decision-a')
  assert.equal(pending.decision, 'APPROVE')
})

test('background reordering, task removal and new versions cannot substitute another decision target', () => {
  const first = task()
  const other = task({ taskId: 'task-b' })
  const pending = freezeReviewDecision(first, 'APPROVE', scope, 'decision-a')
  assert.equal(canConfirmReviewDecision(pending, scope, selectReviewTask([other, first], first.taskId, false)), true)
  assert.equal(canConfirmReviewDecision(pending, scope, selectReviewTask([other], first.taskId, false)), false)
  assert.equal(canConfirmReviewDecision(pending, scope, other), false)
  assert.equal(canConfirmReviewDecision(pending, scope, task({ version: 5 })), false)
  assert.equal(pending.task.taskId, first.taskId)
  assert.equal(pending.task.version, 4)
})

test('confirmation uses the current detail version, even while the inbox still has the prior version', () => {
  const inbox = task()
  const detail = task({ version: 5 })
  const pending = freezeReviewDecision(detail, 'REJECT', scope, 'decision-b')
  assert.equal(canConfirmReviewDecision(pending, scope, currentReviewTask(inbox.taskId, inbox, detail)), true)
  assert.equal(canConfirmReviewDecision(pending, scope, currentReviewTask(inbox.taskId, task({ version: 6 }), detail)), false)
})

test('a changed Brain, target, release, customer or actor invalidates a pending confirmation', () => {
  const current = task()
  const pending = freezeReviewDecision(current, 'APPROVE', scope, 'decision-a')
  for (const [field, value] of Object.entries({ deploymentId: 'brain-b', targetProfileId: 'production', releaseId: 'release-b', reviewer: '["customer-b","actor-b"]' })) {
    assert.equal(canConfirmReviewDecision(pending, { ...scope, [field]: value }, current), false, field)
  }
  assert.equal(canConfirmReviewDecision(pending, { ...scope, reviewer: '' }, current), false)
})

test('only pending, unexpired tasks and explicitly allowed decisions can be confirmed', () => {
  const now = Date.parse('2026-11-08T10:30:00Z')
  assert.equal(canDecideReviewTask(task(), 'APPROVE', now), true)
  assert.equal(canDecideReviewTask(task({ status: 'WAITING_FOR_REVIEW' }), 'APPROVE', now), true)
  assert.equal(canDecideReviewTask(task({ allowedDecisions: ['REJECT'] }), 'APPROVE', now), false)
  assert.equal(canDecideReviewTask(task({ expiresAt: '2026-11-08T10:30:00Z' }), 'REJECT', now), false)
  for (const status of ['APPROVED', 'REJECTED', 'COMPLETED', 'FAILED', 'EXPIRED', 'IN_REVIEW', 'DECIDING', 'WAITING_FOR_INFORMATION']) {
    assert.equal(canDecideReviewTask(task({ status }), 'APPROVE', now), false, status)
  }
  const pending = freezeReviewDecision(task(), 'APPROVE', scope, 'decision-a')
  assert.equal(canConfirmReviewDecision(pending, scope, task({ allowedDecisions: ['REJECT'] })), false)
  assert.equal(canConfirmReviewDecision(pending, scope, task({ status: 'REJECTED' })), false)
})

test('controller retains customer-only authority and existing APIs, with a frozen task/version payload', () => {
  assert.ok(source.includes('isCustomerSession(session)'))
  assert.ok(source.includes('enabled: enabled && Boolean(detailTaskId)'))
  assert.ok(source.includes('fetchDeploymentHumanReviewDetail(selectedDeploymentId, targetProfileId, detailTaskId)'))
  assert.ok(source.includes('if (!customerReviewer)'))
  assert.ok(source.includes('Sign in with a customer administrator session'))
  assert.ok(source.includes('workspaceView = false'))
  assert.ok(source.includes("title = 'Human Review'"))
  assert.ok(source.includes('decideDeploymentHumanReview(input.deploymentId, input.targetProfileId, input.task.taskId'))
  assert.ok(source.includes('expectedVersion: input.task.version, decisionId: input.decisionId'))
  assert.ok(source.includes('decisionMutation.mutate(pendingDecision)'))
  assert.ok(source.includes('retry: false'))
  const directFetches = []
  function visit(node) {
    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'fetch') directFetches.push(node)
    ts.forEachChild(node, visit)
  }
  visit(ast)
  assert.equal(directFetches.length, 0)
})

test('confirmation guards stay busy through pending confirmation, submission and cache refresh', () => {
  assert.ok(source.includes('const busy = pendingDecision != null || decisionMutation.isPending'))
  assert.ok(source.includes('onBusyChange?.(busy)'))
  assert.ok(source.includes('useEffect(() => () => onBusyChange?.(false)'))
  assert.ok(source.includes('if (!decisionMutation.isPending) { setPendingDecision(null)'))
  assert.ok(source.includes('disabled={!confirmationValid || decisionMutation.isPending}'))
  assert.ok(source.includes('pendingDecision.reviewer !== reviewer && !decisionMutation.isPending'))
  assert.ok(source.includes("['deployment-human-reviews', input.deploymentId, input.targetProfileId]"))
  assert.ok(source.includes("['deployment-human-review-detail', input.deploymentId, input.targetProfileId]"))
})

test('workspace contract preserves scoped task URL, explicit missing errors, mobile back and bounded panes', () => {
  assert.ok(source.includes("new URLSearchParams(location.search).get('taskId')"))
  assert.ok(source.includes("params.set('taskId', taskId)"))
  assert.ok(source.includes("params.delete('taskId')"))
  assert.ok(source.includes('search: params.toString(), hash: location.hash'))
  assert.ok(source.includes('selectReviewTask(tasks, requestedTaskId, !workspaceView)'))
  assert.ok(source.includes('The requested review task is unavailable. No other task was selected.'))
  assert.ok(source.includes('detailQuery.error instanceof PlatformApiError && detailQuery.error.status === 404'))
  assert.ok(source.includes('Back to tasks'))
  assert.ok(source.includes("overflow: 'auto', overscrollBehavior: 'contain'"))
  assert.ok(source.includes('flexShrink: 0'))
  assert.ok(source.includes('of {tasks.length} returned tasks'))
  assert.ok(!source.includes('Save draft'))
  assert.ok(!source.includes('Publish'))
})

test('decision receipt, failure and supplied outcome are separate from approval claims', () => {
  assert.ok(source.includes('currentReceipt?.failure?.publicMessage'))
  assert.ok(source.includes('detail?.outcome ?? currentReceipt?.outcome'))
  assert.ok(source.includes('Decision recorded. Execution is reported separately.'))
  assert.ok(source.includes('Approval recorded. Execution outcome is not available.'))
  assert.ok(source.includes('it does not confirm successful execution.'))
  assert.ok(source.includes('Action outcome: {outcome.actionName}'))
  assert.ok(source.includes('receipt.input.task.taskId === detailTaskId'))
})
