import {readFileSync, writeFileSync, mkdirSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import {resolve, dirname} from 'node:path'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const read = path => JSON.parse(readFileSync(resolve(root, path), 'utf8'))
export const supportPrompts = {
  answerGenerationPrompt: 'Answer the current question naturally and directly, using the supplied conversation, approved knowledge and authorized observations. Use short headings only when they genuinely help; do not force Evidence/Outcome sections. Preserve the user\'s issue order. Distinguish documented procedures, saved draft configuration, recorded release verification and an actual live probe. A returned selection or consent requirement is not a completed resource check: explain the next UI step plainly, without presenting internal codes as the answer. State what remains unknown and propose a useful next step. Never claim an unexecuted repair, successful write, agent presence or message delivery. Keep actual receipts and pending confirmations intact.',
  systemAddition: 'Support continuity and specialist assistance: ordinary questions do not require a ticket. Use the existing conversation history for follow-ups. Explain missing selection or diagnostic consent using the Support diagnostic controls; do not ask for secrets or internal domains. Simple documentation questions can be answered directly. Choose a specialist or adaptive resolution only when its declared expertise materially helps. The manager chooses workers and order within its configured allowance, not a fixed mandatory checklist. Specialists are read-only. Draft configuration changes belong in canonical Build with AI; publish/apply/activation and destructive changes remain explicit governed operations. Parent conversation ticket/handover controls retain their actual confirmation and authorization contracts. Do not equate draft metadata, recorded verification or local readiness with an unperformed live API, HTTPS, indexing, agent-presence or delivery probe.'
}

export function diagnosticManifest() {
  const manifest = read('manifests/loomai-support-diagnostics.1.0.4.manifest.json')
  manifest.version = '1.1.0'
  manifest.description = 'Consent-bound, read-only conversation diagnostics for owned Brain releases, knowledge, capability configuration, Engage readiness and human-support configuration. No raw configuration, credentials or private logs.'
  for (const [id, label] of [['integrationStatusUrl','Approved integration configuration endpoint binding'],['handoverStatusUrl','Approved handover configuration endpoint binding']]) {
    manifest.installForm.push({id,label,type:'url',required:true})
  }
  const releases = manifest.contributions.actions.find(a=>a.actionId==='support_get_deployment_status').llmFacts.lists.find(l=>l.target==='releaseObservations')
  releases.maxItems = 8
  releases.includeFields.push('versionId','environment')
  for (const [actionId, field, description, fields] of [
    ['support_get_integration_status','integrationStatusUrl','Review conversation-selected owned Brain SAVED DRAFT capability metadata. This does not test connectivity, execute actions or prove the serving release is configured the same way.', ['observationBasis','draftRevision','capabilityCount','readCapabilityCount','writeCapabilityCount','capabilities','capabilitiesTruncated','providerConnectivityProbed','actionsExecuted','guidance','stateUpdatedAt','code']],
    ['support_get_handover_status','handoverStatusUrl','Review handover/ticket visibility and handover settings from the selected owned app and its EXACT assigned verified release. This does not probe agent presence, ticket-provider health or message delivery.', ['observationBasis','releaseId','targetProfileId','handoverEnabledInAssignedRelease','handoverVisibleInApp','ticketsVisibleInApp','teamVisibleInApp','userCanResumeAi','agentPresenceProbed','messageDeliveryProbed','ticketProviderProbed','observedReleaseAt','guidance','code']]
  ]) manifest.contributions.actions.push({
    actionId,adapterType:'connector-http',description,category:'support-diagnostics',readOnly:true,anonymousAllowed:false,confirmationRequired:false,groundingEligible:true,readActionResolutionEligible:true,requiredScopes:['support:diagnostics:read'],params:[],
    llmFacts:{copyFields:['observedAt','availability','requirement'],lists:[{sourcePath:'resources',target:actionId==='support_get_integration_status'?'integrationObservations':'handoverObservations',maxItems:8,includeFields:['resource.id','availability',...fields.map(f=>'facts.'+f)]}]},
    route:{method:'POST',managedUrlField:field,managedHeaderSecretRefFields:{'X-LOOMAI-SUPPORT-SERVICE-KEY':'diagnosticServiceKey'},response:{'success-http-status':[200]}}
  })
  return manifest
}

export function specialistDefaults() {
  return {generic:read('overlays/generic/specialist-assistance.json'),loomai:read('overlays/loomai/specialist-assistance.json'),prompts:supportPrompts,diagnostics:compoundDiagnosticManifest()}
}

export function compoundDiagnosticManifest() {
  const manifest=read('manifests/loomai-support-diagnostics.1.0.4.manifest.json')
  manifest.version='1.2.0'
  manifest.description='Consent-bound resource diagnostics with recorded release, saved integration and exact assigned handover metadata. Existing approved endpoint and credential contracts are retained; no business actions or live connectivity probes.'
  const deployment=manifest.contributions.actions.find(a=>a.actionId==='support_get_deployment_status')
  deployment.description+=' Includes bounded saved-draft capability metadata under integrationConfiguration; this does not execute tools or test connectivity.'
  deployment.llmFacts.lists.find(l=>l.target==='deploymentObservations').includeFields.push('facts.observationBasis','facts.integrationConfiguration')
  const releases=deployment.llmFacts.lists.find(l=>l.target==='releaseObservations');releases.maxItems=8;releases.includeFields.push('versionId','environment')
  const engage=manifest.contributions.actions.find(a=>a.actionId==='support_get_engage_readiness')
  engage.description+=' Includes handoverConfiguration from the exact app-assigned verified release, not the draft. Agent presence and message delivery are not probed.'
  engage.llmFacts.lists.find(l=>l.target==='readinessObservations').includeFields.push('facts.observationBasis','facts.providerConnectivityProbed','facts.handoverConfiguration')
  return manifest
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const write = (path,value) => { mkdirSync(dirname(path),{recursive:true}); writeFileSync(path,JSON.stringify(value,null,2)+'\n') }
  write(resolve(root,'manifests/loomai-support-diagnostics.1.2.0.manifest.json'),compoundDiagnosticManifest())
  write(resolve(root,'../../Platfrom/backend/src/main/resources/platform-templates/support-specialist-defaults.v2.json'),specialistDefaults())
}
