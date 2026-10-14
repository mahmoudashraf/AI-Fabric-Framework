import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import ts from 'typescript'

const source = readFileSync(new URL('../src/authenticationRequirement.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
  fileName: 'authenticationRequirement.ts',
}).outputText
const {
  extractWorkspaceAuthenticationRequirement,
  WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA,
} = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)

assert.deepEqual(extractWorkspaceAuthenticationRequirement({
  authenticationRequirement: {
    schemaVersion: WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA,
    reasonCode: 'AUTHENTICATION_REQUIRED',
    retryPolicy: 'EXPLICIT_NEW_REQUEST',
    authenticationProfile: 'customer-supabase',
    capability: 'request-account-change',
  },
}), {
  schemaVersion: WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA,
  reasonCode: 'AUTHENTICATION_REQUIRED',
  retryPolicy: 'EXPLICIT_NEW_REQUEST',
  authenticationProfile: 'customer-supabase',
  capability: 'request-account-change',
  message: undefined,
})

assert.equal(extractWorkspaceAuthenticationRequirement({ fallbackReason: 'AUTHENTICATION_REQUIRED' }), undefined)
assert.equal(extractWorkspaceAuthenticationRequirement({
  actions: [{ actionResult: { errorCode: 'AUTHENTICATION_REQUIRED' } }],
}), undefined)

assert.equal(extractWorkspaceAuthenticationRequirement({ fallbackReason: 'ACCESS_DENIED' }), undefined)
assert.equal(extractWorkspaceAuthenticationRequirement({ errorCode: 'CAPABILITY_DENIED' }), undefined)
assert.equal(extractWorkspaceAuthenticationRequirement({ answer: 'Please sign in to continue.' }), undefined)
assert.equal(extractWorkspaceAuthenticationRequirement({
  authenticationRequirement: {
    schemaVersion: 'unknown-contract',
    reasonCode: 'AUTHENTICATION_REQUIRED',
  },
}), undefined)
assert.equal(extractWorkspaceAuthenticationRequirement({
  authenticationRequirement: {
    schemaVersion: WORKSPACE_AUTHENTICATION_REQUIREMENT_SCHEMA,
    reasonCode: 'AUTHENTICATION_REQUIRED',
    retryPolicy: 'AUTOMATIC_REPLAY',
  },
}), undefined)

const chatFlowSource = readFileSync(new URL('../src/hooks/useChatFlow.ts', import.meta.url), 'utf8')
assert.match(chatFlowSource, /extractWorkspaceAuthenticationRequirement\(data\)/)
assert.match(chatFlowSource, /authentication\?\.interactionPolicy === "public-with-protected-capabilities"/)
assert.doesNotMatch(chatFlowSource, /authenticationRequirement[\s\S]{0,900}(?:resendChatQuery|handleChatQuery\()/)

console.log('AI Workspace authentication-requirement smoke passed.')
