import React, { useEffect, useRef } from 'react'
import { createRoot } from 'react-dom/client'
import { createHashRouter, RouterProvider, Navigate, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Alert } from '@mui/material'
import { PlatformAuthProvider, usePlatformAuth } from '../../src/auth/PlatformAuthProvider'
import { DeploymentWorkspaceProvider } from '../../src/workspace/DeploymentWorkspaceContext'
import { CustomerConsoleShell } from '../../src/layout/CustomerConsoleShell'
import { CustomerResourcesPage } from '../../src/pages/CustomerResourcesPage'
import { CustomerKnowledgeConnectionsPage } from '../../src/pages/CustomerKnowledgeConnectionsPage'
import { CustomerActionReviewsPage } from '../../src/pages/CustomerActionReviewsPage'
import { NotificationsPage } from '../../src/pages/NotificationsPage'
import { CustomerAccountPage } from '../../src/pages/CustomerAccountPage'
import type { AIWorkspaceInstallationSummary } from '../../src/api/platformApi'

const scenario = new URLSearchParams(location.search)
declare const __CUSTOMER_PREVIEW_PHASE__: string
const shopify = scenario.get('product') === 'shopify'
const empty = scenario.get('state') === 'first'
const account = scenario.get('state')==='long' ? 'LongCustomerAccountWithoutSpaces'.repeat(3) : shopify ? 'Willow & Finch' : 'Northfield Motor House'
const deployment = scenario.get('state')==='long' ? 'LongDeploymentResourceWithoutSpaces'.repeat(3) : shopify ? 'Store shopping assistant' : 'Northfield assistant'
const appName = shopify ? 'Storefront AI workspace' : 'Northfield AI'
const observed = '2026-10-23T12:00:00Z'
const owner = { enabled:true,authenticated:true,actorId:'preview-owner',displayName:'Jamie Owner',role:'CUSTOMER_ADMIN',authenticationMode:'SESSION',headerName:'X-PLATFORM-API-KEY',sessionAuthEnabled:true,apiKeyAuthEnabled:true,customerId:'preview-customer',customerName:account,customerSlug:'preview',canManageUsers:false,canManageUserDirectory:false,canManageCustomers:false,canCreateCustomers:false,canManageSecrets:false,canOperateDeployments:false }
const offer = { id:'preview-offer',name:shopify?'Shopping experience':'Dealership experience',templateId:'preview-template',templateVersionId:'preview-template-v1',fingerprint:'preview-fingerprint',entitlement:{id:'preview-entitlement',offerId:'preview-offer',customerId:'preview-customer',ownerUserId:'preview-owner',status:'ACTIVE',revision:1,expiresAt:'2099-01-01T00:00:00Z'},targets:[{id:'preview-staging',intent:'CUSTOMER_STAGING',readiness:'READY',capabilities:[],limits:{},blockers:[]},{id:'preview-production',intent:'CUSTOMER_PRODUCTION',readiness:'READY',capabilities:[],limits:{},blockers:[]}],permissions:[],settings:[],bindingSlots:[],workspaceProfiles:[{id:'preview-workspace-profile',label:'Reviewed fixture experience',interaction:{packCode:shopify?'shopify-shopping-workspace':'dealership'},theme:null,authentication:{}}],externalIntegrationProfiles:[],documentAudiences:[],blockers:[] }
const offers = scenario.get('state') === 'multiple-offers' ? [offer, { ...offer, id:'preview-offer-two', name:'Second reviewed template', entitlement:{...offer.entitlement,offerId:'preview-offer-two'} }] : [offer]
if (scenario.get('state') === 'revoked') offer.entitlement.status = 'REVOKED'
if (scenario.get('state') === 'tasks') Object.assign(offer, { bindingSlots: [
  { id: 'inventory', kind: 'EXTERNAL_SERVICE', supportedProfileIds: [] },
  { id: 'policies', kind: 'CUSTOMER_S3', supportedProfileIds: [] },
  { id: 'support', kind: 'INBOX_OPERATOR', supportedProfileIds: [] },
] })
const portfolio = empty ? [] : [{deploymentId:'preview-deployment',name:deployment,environmentName:'Customer staging',status:'ACTIVE',tenantId:'preview-tenant',offerId:offer.id,bootstrapTargetProfileId:'preview-staging',offerFingerprint:offer.fingerprint,authorizationEntitlementRevision:1,activeVersionId:'preview-version',createdAt:observed}]
if (scenario.get('state') === 'scope-mismatch') portfolio.push({ ...portfolio[0], deploymentId: 'preview-other-deployment', name: 'Other owned deployment' })
const release = { id:'preview-production-release',deploymentId:'preview-deployment',deploymentVersionId:'preview-version',status:'APPLIED_VERIFIED',verificationStatus:'PASSED',provisioningStatus:'ACTIVE',provisioningTarget:'preview-production',targetProfileId:'preview-production',providerType:'COOLIFY',sourceArtifactId:'preview-artifact',providerResourceHandleId:null,currentStepKey:null,currentStepDescription:null,errorMessage:null,verificationRunId:'preview-verification',provisioningDetails:null,createdAt:observed,appliedAt:observed,updatedAt:observed }
const apps: AIWorkspaceInstallationSummary[] = empty ? [] : [{ id:'preview-app',installationId:'preview-app',customerId:'preview-customer',consumerId:'preview-consumer',displayName:appName,status:'ACTIVE',experiencePackCode:shopify?'shopify-shopping-workspace':'dealership',experiencePackVersion:'preview-version',connectionMode:shopify?'backend-mediated-private-runtime':'public-runtime-anonymous',connectionProfileCode:shopify?'shopify-private-adapter':'anonymous-runtime',connectionProfileVersion:'preview',connectionConfiguration:{},allowedOrigins:[],configuration:{},deliveryMode:shopify?'embedded':'standalone',appSlug:'preview',appUrl:'https://preview.example.com',interactionPolicy:shopify?'login-required':'public-with-protected-capabilities',authProfileCode:shopify?'private-shopify':'optional-customer-sign-in',authProfileVersion:'preview',authConfiguration:{},domains:[{hostname:'ai.example.com',url:'https://ai.example.com',type:'custom',status:'PENDING',dnsRecordName:'ai',dnsRecordValue:'preview.example.com',routingRecordName:null,routingRecordValue:null,lastVerificationError:'DNS record is not verified. Default app address is unchanged.',verifiedAt:null}],deploymentId:'preview-deployment',releaseId:release.id,assignmentRevision:'preview-opaque-revision',ready:false,readinessChecks:[{code:'CUSTOM_DOMAIN',status:'BLOCKED',message:'Custom domain needs DNS verification.'}],rowVersion:1,createdAt:observed,updatedAt:observed,activatedAt:observed,disabledAt:null }]
if(scenario.get('state')==='draft' && apps[0]){apps[0].status='DRAFT';apps[0].activatedAt=null}
if (scenario.get('state') === 'history') {
  portfolio.push({ ...portfolio[0], deploymentId:'preview-old-deployment', name:'Unused configuration', status:'APPLY_FAILED' })
  apps.push({ ...apps[0], id:'old-app', installationId:'old-app', displayName:'Old disabled app', status:'DISABLED', readinessChecks:[{code:'OLD_BINDING',status:'BLOCKED',message:'Old draft binding missing'}] })
}
const requests: Array<{method:string;path:string}> = []
let taskPreferences: Record<string, unknown> = {}
const targetPath = '/api/deployments/preview-deployment/targets/preview-production/releases/preview-production-release'
const offerDeploymentPath = '/api/customer-self-service/catalogue/preview-offer/deployments/preview-deployment'
const source = {sourceId:'preview-policy',datasetId:'preview-documents',displayName:shopify?'Returns and delivery policy':'Collection and delivery policy',objectLocatorDigest:'fixture-digest',connectorType:'S3_COMPATIBLE_OBJECT_STORAGE',connectorBindingRef:'preview-storage',mediaType:'text/plain',contentLength:2400,candidateVersion:2,activeVersion:1,providerRevisionFingerprint:'fixture-revision',providerVersionDigest:null,etagDigest:null,providerLastModified:observed,visibility:'public',status:'ACTIVE',failureCode:null,failureMessage:null,updatedAt:observed}
const integrationSource = {sourceId:shopify?'shopify-catalogue':'advertiser-stock',enabled:true,connectionProfileRef:'preview-connection',protectedResourceBindingRef:'preview-resource',vectorSpace:shopify?'product':'dealer-vehicle',entityType:'fixture-record',scheduleSeconds:300,freshnessState:'CURRENT',preflightState:'PASSED',state:{sourceId:'preview-source',status:'COMPLETED',cursor:null,sourceVersion:null,providerCorrelationHeader:null,providerCorrelationValue:null,counts:{sourceCount:6,normalizedCount:6,indexedCount:6,deletedCount:0,acceptedWorkCount:6,completedWorkCount:6,failedWorkCount:0},lastStartedAt:observed,lastSuccessAt:observed,lastErrorAt:null,errorClass:null,errorMessage:null,updatedAt:observed}}
;(window as unknown as {previewRequests:typeof requests}).previewRequests = requests
// This transport is bundled only in the offline review build, never the real UI.
window.fetch = async (input, init) => {
  const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, location.origin)
  const method = init?.method ?? (input instanceof Request ? input.method : 'GET')
  requests.push({method,path:url.pathname})
  if (scenario.get('state') === 'tasks' && method === 'PUT' && url.pathname === '/api/platform/preferences') {
    taskPreferences = JSON.parse(String(init?.body ?? '{}'))
    return new Response(JSON.stringify(taskPreferences), { headers: { 'Content-Type': 'application/json' } })
  }
  if (method !== 'GET') return new Response(JSON.stringify({message:'Offline read-only preview: no mutation is permitted.'}),{status:405,headers:{'Content-Type':'application/json'}})
  let value: unknown
  if(url.pathname==='/api/platform/auth/session')value=owner
  else if(url.pathname==='/api/customer-self-service/deployments')value=portfolio
  else if(url.pathname==='/api/customer-self-service/catalogue')value=scenario.get('state')==='catalogue-error'?null:offers
  else if(url.pathname==='/api/platform/customers/preview-customer/ai-workspace-installations')value=scenario.get('state')==='apps-error'?null:apps
  else if(url.pathname==='/api/deployments/preview-deployment/releases')value=scenario.get('state')==='release-error'?null:[release]
  else if(url.pathname==='/api/platform/customers')value=[{id:'preview-customer',name:account,status:'ACTIVE',tenants:[],consumers:[{consumerId:'preview-consumer',displayName:appName,customerId:'preview-customer',status:'ACTIVE',boundDeploymentId:'preview-deployment',boundReleaseId:release.id,boundTargetProfileId:'preview-production',bindingRevision:1}]}]
  else if(url.pathname==='/api/platform/ai-workspaces/catalog')value={assetsReady:true,assetStatus:'REVIEW_FIXTURE',experiencePacks:[],connectionProfiles:[],authenticationProfiles:[]}
  else if(url.pathname==='/api/platform/preferences')value=taskPreferences
  else if(url.pathname==='/api/customer-self-service/invitations')value=[]
  else if(url.pathname==='/api/platform/customers/preview-customer/consumers')value=[{id:'preview-consumer-entity',consumerId:'preview-consumer',displayName:appName,customerId:'preview-customer',status:'ACTIVE',boundDeploymentId:'preview-deployment',boundReleaseId:release.id,boundTargetProfileId:'preview-production',boundReleaseStatus:release.status,bindingRevision:1}]
  else if(url.pathname==='/api/deployments/preview-deployment/versions')value=[]
  else if(url.pathname===`${offerDeploymentPath}/admission-reservations`||url.pathname===`${offerDeploymentPath}/retirements`)value=[]
  else if(url.pathname===`${offerDeploymentPath}/go-live-readiness`)value={deploymentId:'preview-deployment',offerId:offer.id,versionId:'preview-version',releaseId:release.id,targetProfileId:'preview-production',candidateFingerprint:'fixture-fingerprint',consumerId:'preview-consumer',expectedBindingRevision:1,activationReady:false,postActivationComplete:false,blockers:['DEMO_FIXTURE_ONLY'],items:[{code:'PRODUCTION_DOCUMENT_EVIDENCE',category:'KNOWLEDGE',label:'Production document evidence',phase:'ACTIVATION',status:'BLOCKED',mandatory:true,owner:'Customer',evidenceReference:null,checkedAt:observed,expiresAt:null,blocker:'This fictional review fixture has not run production verification.',nextAction:'Verify the exact production release before activation.'}],projectedAt:observed}
  else if(url.pathname==='/api/deployments')value=portfolio.map(item=>({id:item.deploymentId,name:item.name,environment:item.environmentName,templateId:'preview-template',behaviorType:'CONVERSATIONAL',status:item.status,activeVersion:'preview-version',source:{},binding:{customerId:'preview-customer',tenantId:'preview-tenant'},runtimeBaseUrl:null,connectorProvisioned:true,approvalRequiredForApply:true,approvalRequiredForDelete:true,createdAt:observed}))
  else if(['/api/deployments/preview-deployment/workspace', '/api/deployments/preview-other-deployment/workspace'].includes(url.pathname))value={access:{canAdmin:scenario.get('editable')==='1',canOperate:false},documentKnowledgeConfigured:true,externalIntegrationConfigured:true,conversationHandoverConfigured:scenario.get('state')!=='no-support'}
  else if(url.pathname===`${targetPath}/context`)value={deploymentId:'preview-deployment',targetProfileId:'preview-production',releaseId:release.id,versionId:'preview-version',environmentName:'Customer production',releaseStatus:release.status,configHash:'fixture-hash',runtimeBaseUrl:'https://runtime.example.com',connectorBaseUrl:null,documentDatasetIds:['preview-documents'],externalIntegrationConfigured:true,conversationHandoverConfigured:scenario.get('state')!=='no-support'}
  else if(url.pathname==='/api/deployments/preview-deployment/document-storage-bindings')value=[{bindingRef:'preview-storage',deploymentId:'preview-deployment',targetProfileId:'preview-production',connectorType:'S3_COMPATIBLE_OBJECT_STORAGE',status:'ACTIVE',credentialsPresent:true,safeMetadata:{},updatedAt:observed}]
  else if(url.pathname===`${targetPath}/document-knowledge/sources`)value=scenario.get('state')==='documents-error'?null:[source]
  else if(url.pathname===`${targetPath}/document-knowledge/sources/preview-policy`)value={source,manifests:[]}
  else if(url.pathname===`${targetPath}/document-knowledge/connector`)value={ready:true,connectorType:'S3_COMPATIBLE_OBJECT_STORAGE',bindingRef:'preview-storage',scopeDigest:'fixture-scope',objectVersioningAvailable:false,errorCode:null,checkedAt:observed}
  else if(url.pathname===`${targetPath}/document-knowledge/retention`)value={evidenceRetention:'P30D',commandRetention:'P7D',batchSize:100,lastCleanup:null}
  else if(url.pathname===`${targetPath}/integrations`)value={success:true,persistence:{enabled:true,schema:'fixture'},connectionProfiles:[],protectedResources:[],tokenPosture:{},sources:[integrationSource],webhooks:[],runtimeDataSync:{enabled:true,baseUrlConfigured:true,serviceCredentialConfigured:true}}
  else if(url.pathname===`${targetPath}/integrations/conversation-handover`)value=scenario.get('state')==='support-error'?null:{success:true,runtime:{enabled:true,channelBindingId:'preview-basic-inbox',handoverCounts:{HUMAN_ACTIVE:1},dispatchCounts:{DELIVERED:1},oldestPendingDispatchAt:null,recentDispatches:[]},connector:{reachable:true,statusCode:200,status:{enabled:true,bindingId:'preview-basic-inbox',providerConfigured:true,runtimeConfigured:true,callbackRecoveryOwner:'CONNECTOR',manualCallbackReplaySupported:false,rejectedCallbackCount:0,callbackCounts:{ACCEPTED:1},recentCallbacks:[]}}}
  else if(url.pathname==='/api/deployments/preview-deployment/human-reviews')value=scenario.get('state')==='reviews-error'?null:[]
  else if(url.pathname==='/api/customer-self-service/admission/notices')value=scenario.get('state')==='notices-error'?null:[]
  else if(url.pathname==='/api/deployments/preview-deployment/draft')value={deploymentId:'preview-deployment',marketplaceDatasetConfig:{datasets:[{datasetId:'preview-documents',ingestionMode:'EXTERNAL_DOCUMENT_STORAGE',marketplaceInstallId:'preview-document-install'}]}}
  else if(url.pathname==='/api/deployments/preview-deployment/marketplace-installs')value=[]
  else value=null
  if (scenario.get('state') === 'delivery-failure' && url.pathname === `${targetPath}/integrations/conversation-handover` && value) {
    const support = value as { runtime: { dispatchCounts: Record<string, number>; recentDispatches: unknown[] }; connector: { status: { rejectedCallbackCount: number; recentCallbacks: unknown[] } } }
    support.runtime.dispatchCounts = { DEAD: 1 }
    support.runtime.recentDispatches = [{ dispatchId: 'fixture-dispatch', dispatchType: 'OPEN_HANDOVER', status: 'DEAD', failureCode: 'PROVIDER_UNAVAILABLE' }]
    support.connector.status.rejectedCallbackCount = 1
    support.connector.status.recentCallbacks = [{ eventId: 'fixture-callback', eventType: 'MESSAGE', errorClass: 'INVALID_SIGNATURE' }]
  }
  if (scenario.get('state') === 'multiple-assignments' && url.pathname === '/api/platform/customers' && Array.isArray(value)) {
    value[0].consumers.push({ ...value[0].consumers[0], consumerId: 'preview-consumer-two', displayName: 'Second product assignment' })
  }
  return new Response(JSON.stringify(value ?? {message:'This read is unavailable in the current offline scenario.'}),{status:value==null?503:200,headers:{'Content-Type':'application/json'}})
}
const client=new QueryClient({defaultOptions:{queries:{retry:false,staleTime:60000}}})
function Preview(){
  const auth=usePlatformAuth()
  if(auth.isLoading)return <p>Loading offline scenario...</p>
  if(!auth.session)return <Alert severity="error">Preview session unavailable.</Alert>
  return <DeploymentWorkspaceProvider><CustomerConsoleShell session={auth.session} onSignOut={auth.signOut}>
    <Routes><Route path="/home" element={<CustomerResourcesPage mode="home"/>}/><Route path="/deployments" element={<CustomerResourcesPage mode="deployments"/>}/><Route path="/ai-workspaces" element={<CustomerResourcesPage mode="apps"/>}/><Route path="/document-knowledge" element={<CustomerKnowledgeConnectionsPage mode="knowledge"/>}/><Route path="/integrations" element={<CustomerKnowledgeConnectionsPage mode="connections"/>}/><Route path="/human-support" element={<CustomerKnowledgeConnectionsPage mode="human"/>}/><Route path="/human-review" element={<CustomerActionReviewsPage/>}/><Route path="/notifications" element={<NotificationsPage/>}/><Route path="/users" element={<CustomerAccountPage/>}/><Route path="/behavior" element={<Alert severity="warning">Behaviour management is not available under the customer console contract. Staff-only configuration is not exposed.</Alert>}/><Route path="/" element={<Navigate to="/home" replace/>}/><Route path="*" element={<Alert severity="info">This offline phase preview includes resource and configuration views. Later customer capabilities are not claimed here.</Alert>}/></Routes>
  </CustomerConsoleShell></DeploymentWorkspaceProvider>
}
const router=createHashRouter([{path:'*',element:<Preview/>}])
function PreviewFrame(){
  const banner=useRef<HTMLDivElement>(null)
  useEffect(()=>{if(!banner.current)return;const observer=new ResizeObserver(()=>document.documentElement.style.setProperty('--preview-banner-height',`${banner.current?.offsetHeight??38}px`));observer.observe(banner.current);return()=>observer.disconnect()},[])
  return <><style>{'.preview-shell .MuiDrawer-docked .MuiDrawer-paper { top: var(--preview-banner-height, 38px); height: calc(100vh - var(--preview-banner-height, 38px)); }'}</style><div ref={banner} style={{background:'#24272d',color:'#f4f5f7',padding:'9px 16px',font:'12px/1.5 sans-serif',display:'flex',flexWrap:'wrap',gap:'10px 20px'}}>
    <strong>{__CUSTOMER_PREVIEW_PHASE__} IMPLEMENTATION PREVIEW</strong><span>Actual resource reads and UI / Offline fixture transport / No live calls</span>
    <a style={{color:'#c4ceff'}} href="?product=dealership">Dealership</a><a style={{color:'#c4ceff'}} href="?product=shopify">Shopify</a><a style={{color:'#c4ceff'}} href="?state=first">First setup</a><a style={{color:'#c4ceff'}} href="?state=apps-error">Read error</a>
  </div>
  <div className="preview-shell"><QueryClientProvider client={client}><PlatformAuthProvider><RouterProvider router={router}/></PlatformAuthProvider></QueryClientProvider></div></>
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><PreviewFrame/></React.StrictMode>)
