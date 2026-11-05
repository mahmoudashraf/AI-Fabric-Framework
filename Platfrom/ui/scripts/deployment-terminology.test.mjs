import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { deploymentTerminologySource, withDeploymentTerminology } from './deployment-terminology-test-support.mjs'

const read = path => readFile(new URL(path, import.meta.url), 'utf8')
function evaluate(source, dependencies = {}) {
  const code = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS,
  } }).outputText
  const exports = {}
  new Function('exports', ...Object.keys(dependencies), code)(exports, ...Object.values(dependencies))
  return exports
}

async function navigation(path, name, terminology) {
  const source = await read(path)
  const ast = ts.createSourceFile(path, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  const declaration = ast.statements.filter(ts.isVariableStatement)
    .flatMap(node => [...node.declarationList.declarations]).find(node => node.name.getText(ast) === name)
  assert.ok(declaration, `${name} must remain a real navigation declaration`)
  const transformed = ts.transform(declaration.initializer, [context => {
    const visit = node => ts.isPropertyAssignment(node) && node.name.getText(ast) === 'icon'
      ? undefined : ts.visitEachChild(node, visit, context)
    return node => ts.visitNode(node, visit)
  }])
  try {
    const initializer = ts.createPrinter().printNode(ts.EmitHint.Expression, transformed.transformed[0], ast)
    return evaluate(`export const navigation = ${initializer}`, { deploymentTerm: terminology }).navigation
  } finally {
    transformed.dispose()
  }
}

test('UI defaults to Smart Brain in singular, plural and lowercase forms', () => {
  const terminology = evaluate(deploymentTerminologySource())
  assert.equal(terminology.DEPLOYMENT_TERMINOLOGY, 'smart-brain')
  assert.deepEqual(terminology.deploymentTerm, {
    singular: 'Smart Brain', plural: 'Smart Brains', lowercase: 'smart brain', pluralLowercase: 'smart brains',
  })
})

test('the one-line configuration switch restores Deployment forms', () => {
  const terminology = evaluate(deploymentTerminologySource('deployment'))
  assert.equal(terminology.DEPLOYMENT_TERMINOLOGY, 'deployment')
  assert.deepEqual(terminology.deploymentTerm, {
    singular: 'Deployment', plural: 'Deployments', lowercase: 'deployment', pluralLowercase: 'deployments',
  })
})

test('enum display mapping distinguishes Event-driven behavior and never rewrites arbitrary strings', () => {
  for (const mode of ['smart-brain', 'deployment']) {
    const { deploymentTerm, deploymentEnumLabel: label } = evaluate(deploymentTerminologySource(mode))
    assert.equal(label('DEPLOYMENT_ADMIN'), `${deploymentTerm.singular} admin`)
    assert.equal(label('DELETE_DEPLOYMENT'), `Delete ${deploymentTerm.lowercase}`)
    assert.equal(label('SMART_BRAIN'), 'Event-driven')
    for (const untouched of ['Customer Deployment', 'SMART_BRAIN customer name', '/deployments', 'deploymentId',
      'DEPLOYMENT_FROM_FUTURE_API', '__proto__', 'constructor', 'Server deployment failed']) {
      assert.equal(label(untouched), untouched)
    }
    assert.equal(label('CONVERSATIONAL', 'Conversational'), 'Conversational')
  }
})

test('customer and operator navigation change labels but retain identical route and visibility tokens on rollback', async () => {
  for (const [path, name] of [['../src/layout/AppShell.tsx', 'navItems'], ['../src/layout/CustomerConsoleShell.tsx', 'navigation']]) {
    const current = await navigation(path, name, evaluate(deploymentTerminologySource()).deploymentTerm)
    const reverted = await navigation(path, name, evaluate(deploymentTerminologySource('deployment')).deploymentTerm)
    const resource = items => items.find(item => item.path === '/deployments')
    assert.equal(resource(current).label, 'Smart Brains')
    assert.equal(resource(reverted).label, 'Deployments')
    const contracts = items => items.map(({ label, customerLabel, ...contract }) => contract)
    assert.deepEqual(contracts(current), contracts(reverted))
  }
})

test('resource display changes preserve user names, IDs, kind and server messages in both modes', async () => {
  const source = await read('../src/components/customer/customerResourceModels.ts')
  const deployment = { deploymentId: 'deployment/owned', name: 'My Deployment SMART_BRAIN', status: 'DRAFT',
    environmentName: 'My deployment environment', offerId: 'deployment-offer', bootstrapTargetProfileId: 'deployment-target' }
  const before = structuredClone(deployment)
  for (const mode of ['smart-brain', 'deployment']) {
    const { deploymentResource, releaseIssues } = evaluate(withDeploymentTerminology(source, mode))
    const resource = deploymentResource(deployment)
    assert.equal(resource.subtitle, `${mode === 'deployment' ? 'Deployment' : 'Smart Brain'} configuration`)
    assert.equal(resource.name, deployment.name)
    assert.equal(resource.id, deployment.deploymentId)
    assert.equal(resource.deploymentId, deployment.deploymentId)
    assert.equal(resource.kind, 'deployment')
    const serverMessage = 'Customer deployment failed: SMART_BRAIN'
    assert.equal(releaseIssues({ errorMessage: serverMessage, verificationStatus: 'PASSED' })[0].message, serverMessage)
    assert.deepEqual(deployment, before)
  }
})

test('default and reverted terminology leave actual API endpoint, payload keys and enum tokens unchanged', async () => {
  const source = await read('../src/api/platformApi.ts')
  assert.doesNotMatch(source, /from ['"].*deploymentTerminology/)
  const ast = ts.createSourceFile('platformApi.ts', source, ts.ScriptTarget.Latest, true)
  const functions = ast.statements.filter(node => ts.isFunctionDeclaration(node)
    && ['createDeployment', 'upsertDeploymentAssignment', 'createDeploymentApproval'].includes(node.name?.text))
    .map(node => node.getText(ast)).join('\n')
  for (const mode of ['smart-brain', 'deployment']) {
    const calls = []
    const api = evaluate(`${deploymentTerminologySource(mode)}\n${functions}`, {
      request: (...args) => { calls.push(args); return Promise.resolve({}) },
    })
    const payload = { name: 'My Deployment SMART_BRAIN', templateId: 'deployment-template', behaviorType: 'SMART_BRAIN' }
    await api.createDeployment(payload)
    await api.upsertDeploymentAssignment('deployment-one', { userId: 'user-one', assignmentRole: 'DEPLOYMENT_OPERATOR' })
    await api.createDeploymentApproval('deployment-one', { operationType: 'DELETE_DEPLOYMENT', reason: 'User deployment reason' })
    assert.deepEqual(calls.map(([path]) => path), ['/api/deployments', '/api/deployments/deployment-one/assignments',
      '/api/deployments/deployment-one/approvals'])
    assert.deepEqual(JSON.parse(calls[0][1].body), payload)
    assert.equal(JSON.parse(calls[1][1].body).assignmentRole, 'DEPLOYMENT_OPERATOR')
    assert.equal(JSON.parse(calls[2][1].body).operationType, 'DELETE_DEPLOYMENT')
    assert.equal(JSON.parse(calls[2][1].body).reason, 'User deployment reason')
  }
})
