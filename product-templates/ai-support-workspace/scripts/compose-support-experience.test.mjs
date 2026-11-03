import { readFileSync } from 'node:fs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { composeSupportExperience } from './compose-support-experience.mjs';

function installation() {
  return { consumerId: 'owned-consumer', displayName: 'Acme AI', experiencePackCode: 'support', experiencePackVersion: '1.0.0',
    rowVersion: 7, configuration: JSON.parse(readFileSync(new URL('../fixtures/support-experience-config.json', import.meta.url))),
    connectionMode: 'public-runtime-anonymous', connectionProfileCode: 'runtime-anonymous-direct', connectionProfileVersion: '1.0.0',
    connectionConfiguration: {}, allowedOrigins: ['https://support.example.test'], deliveryMode: 'embedded', appSlug: null,
    interactionPolicy: 'public-with-protected-capabilities', authProfileCode: 'supabase', authProfileVersion: '1.0.0',
    authConfiguration: { projectUrl: 'https://auth.example.test', methods: ['magic-link'] },
    domains: [{ type: 'CUSTOMER_CUSTOM', hostname: 'help.example.test' }, { type: 'PLATFORM_MANAGED', hostname: 'acme.apps.example.test' }] };
}

test('composes ordinary revision-checked update without changing identity, origins or source input', () => {
  const current = installation(); const original = structuredClone(current);
  const result = composeSupportExperience(current, { workspaceName: ' Acme Support ', brandColor: '#15803d', teamVisible: true });
  assert.equal(result.displayName, 'Acme Support'); assert.equal(result.rowVersion, 7);
  assert.equal(result.configuration.app.assistantLabel, 'Acme Support');
  assert.equal(result.configuration.theme.primaryColor, '#15803d');
  assert.deepEqual(result.authConfiguration, current.authConfiguration);
  assert.deepEqual(result.allowedOrigins, current.allowedOrigins);
  assert.deepEqual(result.customDomains, ['help.example.test']);
  assert.deepEqual(current, original);
});

test('ticket-only presentation preserves existing provider references without enabling humans', () => {
  const current = installation(); Object.assign(current.configuration.support, { spaceId: 'owned-space', ticketProviderBindingId: 'owned-binding' });
  const result = composeSupportExperience(current, { ticketingEnabled: true, humanSupportEnabled: false });
  assert.equal(result.configuration.capabilities.tickets, true); assert.equal(result.configuration.capabilities.handover, false);
  assert.equal(result.configuration.support.ticketProviderBindingId, 'owned-binding');
});

test('unbound draft preference is not activation; refuses identity injection and stale contracts', () => {
  const draft = composeSupportExperience(installation(), { ticketingEnabled: true });
  assert.equal(draft.configuration.capabilities.tickets, true);
  assert.equal(draft.configuration.support.spaceId, undefined);
  for (const field of ['requesterId', 'authConfiguration', 'ticketProviderBindingId', 'requesterOrigin', 'secretRef']) {
    assert.throws(() => composeSupportExperience(installation(), { [field]: 'untrusted' }), /Unknown/);
  }
  for (const patch of [{ rowVersion: null }, { experiencePackVersion: '0.9.0' }, { experiencePackCode: 'generic-workspace' }]) {
    assert.throws(() => composeSupportExperience({ ...installation(), ...patch }, {}), /current support installation/);
  }
});

test('bounded topics and booleans are validated before draft composition', () => {
  for (const topics of [[{ code: 'a', label: 'A' }, { code: 'a', label: 'B' }], [{ code: 'bad/path', label: 'Bad' }]]) {
    assert.throws(() => composeSupportExperience(installation(), { topics }), /topics/);
  }
  assert.throws(() => composeSupportExperience(installation(), { humanSupportEnabled: 'true' }), /boolean/);
  const topics = [{ code: 'setup', label: 'Setup' }];
  assert.deepEqual(composeSupportExperience(installation(), { topics }).configuration.support.topics, topics);
});

test('illustrative tickets use the canonical provider-neutral shape and opaque versions', () => {
  const fixture = JSON.parse(readFileSync(new URL('../fixtures/support-requests.json', import.meta.url)));
  assert.equal(fixture.illustrativeOnly, true);
  for (const ticket of fixture.items) {
    assert.equal(ticket.providerBindingId, fixture.providerBindingId);
    assert.equal(typeof ticket.version, 'string');
    assert.ok(Number.isNaN(Number(ticket.version)));
    assert.equal(typeof ticket.openingMessageRequired, 'boolean');
    assert.equal(typeof ticket.unread, 'boolean');
    assert.ok(Array.isArray(ticket.episodes) && Array.isArray(ticket.events));
    assert.ok(ticket.capabilities.includes('GET'));
    assert.equal(ticket.episodes[0].conversationId, ticket.conversation.conversationId);
    assert.ok(['OPEN', 'RESOLVED', 'CANCELLED'].includes(ticket.status));
  }
});
