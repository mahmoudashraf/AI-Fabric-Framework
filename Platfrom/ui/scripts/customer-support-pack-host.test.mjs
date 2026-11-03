import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile, access } from 'node:fs/promises'

const source = file => readFile(new URL(`../src/${file}`, import.meta.url), 'utf8')

test('Console mounts shared pack and isolated helper, not a custom transcript or ticket renderer', async () => {
  const workspace = await source('components/customer/SupportWorkspace.tsx')
  assert.match(workspace, /support-experience\/src\/index/)
  assert.match(workspace, /installer\/isolatedHost/)
  assert.match(workspace, /mountInstallation\(context\)/)
  assert.match(workspace, /mounted\.setVisible/)
  assert.match(workspace, /\.setTheme\(/)
  assert.doesNotMatch(workspace, /srcDoc|postMessage|supportWorkspaceDocument|participantMessages|completedTurns/)
})

test('request transport, versions and retries are delegated to the pack; context has separate revisions', async () => {
  const api = await source('api/supportApi.ts')
  assert.match(api, /createRequesterApi\(context,/)
  assert.match(api, /expectedRevision: context\.revision/)
  assert.match(api, /expectedVersion: context\.consent\.version/)
  assert.match(api, /team\.agents\.map/)
  assert.match(api, /profileRef: agent\.agentReference/)
  assert.match(api, /URL\.revokeObjectURL/)
  assert.doesNotMatch(api, /\/cases|SupportCase|initialMessageDelivery: SupportDelivery|createSupportCase|changeSupportStatus/)
})

test('presentation context has no second case store or create/start lifecycle', async () => {
  const context = await source('components/customer/CustomerSupportContext.tsx')
  assert.match(context, /ScopedCustomerSupportProvider key=\{scope\}/)
  assert.match(context, /!isCustomerSession\(auth\.session\)/)
  assert.doesNotMatch(context, /auth\.session!/)
  assert.match(context, /loomai:console:support-kit:/)
  assert.match(context, /current\.openRequest\(next\.id\)/)
  assert.doesNotMatch(context, /useQuery|useMutation|createSupportCase|startSupportConversation|savedCase|pendingIntake|commandKey/)
  const page = await source('pages/CustomerSupportPage.tsx')
  assert.doesNotMatch(page, /SupportRequestList|SupportCaseDetails|useQuery|useMutation|TextField/)
})

test('obsolete form, roster and executable bridge are removed', async () => {
  for (const file of ['SupportRequestForm.tsx', 'SupportTeam.tsx', 'supportWorkspaceDocument.ts', 'useSupportStaffAccess.ts']) {
    await assert.rejects(access(new URL(`../src/components/customer/${file}`, import.meta.url)), { code: 'ENOENT' })
  }
  const dialog = await source('components/customer/SupportResourceDialog.tsx')
  assert.match(dialog, /retry: false/)
  assert.match(dialog, /disabled=\{busy \|\| dirty\}/)
  assert.doesNotMatch(dialog, /\/resolve|\/cancel|\/reopen|createSupportCase|SupportCase/)
})

test('former team intake is navigation to trusted Desk, not another ticket queue', async () => {
  const page = await source('pages/SupportTeamIntakePage.tsx')
  const api = await source('api/supportApi.ts')
  assert.match(page, /deskSignInUrl\(auth\.customerConfig\)/)
  assert.doesNotMatch(page, /useQuery|useMutation|Table|SupabaseIdentityForm|listSupportStaffIntake/)
  assert.doesNotMatch(api, /staff\/intake|listSupportStaffIntake|SupportStaffIntake/)
})

test('bootstrap guards exact BFF targets and Platform-origin SRI assets', async () => {
  const workspace = await source('components/customer/SupportWorkspace.tsx')
  assert.match(workspace, /runtime\.pathname !== expected/)
  assert.match(workspace, /runtime\.origin !== origin/)
  assert.match(workspace, /\/api\/support\/requests\//)
  assert.match(workspace, /\/api\/support\/conversations\//)
  assert.match(workspace, /encodeURIComponent\(value\.conversationId\)/)
  assert.match(workspace, /sha384-/)
  assert.match(workspace, /value\.assignmentRevision !== initial\.assignmentRevision/)
  assert.doesNotMatch(workspace, /value\.principalNamespace !== initial\.principalNamespace/)
  assert.match(workspace, /assertOwner\(\)/)
})

test('Railway UI image includes the shared support pack and complete installer source graph', async () => {
  const dockerfile = await readFile(new URL('../deploy/railway/Dockerfile', import.meta.url), 'utf8')
  assert.match(dockerfile, /^COPY experience-packs\/support-experience\/src \/workspace\/experience-packs\/support-experience\/src$/m)
  assert.match(dockerfile, /^COPY max-mode-widget\/src \/workspace\/max-mode-widget\/src$/m)
  assert.doesNotMatch(dockerfile, /^COPY max-mode-widget\/src\/preview\/configuration\.ts /m)
})
