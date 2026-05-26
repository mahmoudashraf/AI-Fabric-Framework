import { z } from 'zod'

const nullableString = z.string().nullable().optional()
const nullableDateString = z.string().nullable().optional()

export const partnerAccountSchema = z.object({
  id: z.string(),
  name: z.string(),
  status: z.string(),
})

export const partnerMemberSchema = z.object({
  id: z.string(),
  email: z.string(),
  emailVerified: z.boolean(),
  displayName: nullableString,
  avatarUrl: nullableString,
  role: z.string(),
  status: z.string(),
  privileges: z.array(z.string()),
  effectivePermissions: z.array(z.string()),
})

export const partnerSessionSchema = z.object({
  authenticated: z.boolean(),
  signupRequired: z.boolean(),
  account: partnerAccountSchema.nullable(),
  member: partnerMemberSchema.nullable(),
  assignedStoreCount: z.number(),
  openEscalationCount: z.number(),
  permissions: z.array(z.string()),
})

export const partnerProductPackageSchema = z.object({
  profileKey: nullableString,
  packageKey: nullableString,
  tierKey: nullableString,
  displayName: nullableString,
  costPosture: nullableString,
  verificationPackId: nullableString,
  lastConfiguredAt: nullableDateString,
})

export const partnerStoreSchema = z.object({
  id: z.string(),
  storeConnectionId: nullableString,
  shopDomain: z.string(),
  merchantName: z.string(),
  plan: z.string(),
  status: z.string(),
  enabledSurfaces: z.array(z.string()),
  knowledgeSyncStatus: z.string(),
  readinessStatus: z.string(),
  topBlocker: z.string(),
  lastActivityAt: nullableDateString,
  assignmentStatus: z.string(),
  assignmentSource: nullableString,
  approvedBy: nullableString,
  approvedAt: nullableDateString,
  revokedAt: nullableDateString,
  assignmentCreatedAt: nullableDateString,
  assignmentUpdatedAt: nullableDateString,
  installStatus: z.string(),
  widgetStatus: z.string(),
  lastSyncAt: nullableDateString,
  lastWebhookAt: nullableDateString,
  enabledSourceCategories: z.array(z.string()),
  permissions: z.array(z.string()),
  packageProfile: partnerProductPackageSchema.nullable().optional(),
})

const partnerProductWidgetSettingsBaseSchema = z.object({
  launcherLabel: z.string(),
  welcomeMessage: z.string(),
  shellModeProfile: z.string(),
  colorScheme: nullableString,
  debugEnabled: z.boolean(),
  assistantDockEnabled: z.boolean().optional(),
  askAssistantLauncherEnabled: z.boolean().optional(),
  enabledSurfaces: z.array(z.string()),
  defaultConversationMode: z.string(),
  allowedConversationModes: z.array(z.string()),
  pageModeMappings: z.record(z.string()),
})

export const partnerProductWidgetSettingsSchema = partnerProductWidgetSettingsBaseSchema.transform((settings) => ({
  ...settings,
  colorScheme: settings.colorScheme ?? 'graphite',
}))

export const partnerProductSupportProfileSchema = z.object({
  contactEmail: nullableString,
  contactUrl: nullableString,
  helpCenterUrl: nullableString,
  orderLookupPageUrl: nullableString,
  supportPolicyNote: nullableString,
  merchantHandoffConfigured: z.boolean(),
})

export const partnerProductSourceSettingsSchema = z.object({
  productsEnabled: z.boolean(),
  collectionsEnabled: z.boolean(),
  pagesEnabled: z.boolean(),
  policiesEnabled: z.boolean(),
  articlesEnabled: z.boolean(),
  metaobjectsEnabled: z.boolean(),
  enabledCategories: z.array(z.string()),
  lastSourcePreflightAt: nullableDateString,
  lastSyncAt: nullableDateString,
})

export const partnerPackageTrialActivationSchema = z.object({
  id: z.string(),
  shopDomain: z.string(),
  packageKey: z.string(),
  tierKey: z.string(),
  status: z.string(),
  trialDays: z.number(),
  trialStartsAt: nullableDateString,
  trialEndsAt: nullableDateString,
  activatedAt: nullableDateString,
  deactivatedAt: nullableDateString,
  activatedByMemberId: nullableString,
  deactivatedByMemberId: nullableString,
  activationReason: nullableString,
  deactivationReason: nullableString,
  previousTierKey: nullableString,
  previousBillingStatus: nullableString,
  activationProvisioningJobId: nullableString,
  deactivationProvisioningJobId: nullableString,
  pastDue: z.boolean(),
  deactivationEligible: z.boolean(),
  updatedAt: nullableDateString,
})

export const partnerProductControlSchema = z.object({
  storeId: z.string(),
  storeConnectionId: z.string(),
  shopDomain: z.string(),
  merchantName: z.string(),
  assignmentStatus: z.string(),
  installStatus: z.string(),
  widgetStatus: z.string(),
  knowledgeSyncStatus: z.string(),
  readinessStatus: z.string(),
  sourceSettings: partnerProductSourceSettingsSchema,
  enabledSurfaces: z.array(z.string()),
  widgetSettings: partnerProductWidgetSettingsSchema,
  supportProfile: partnerProductSupportProfileSchema,
  capabilities: z.array(z.string()),
  activePackageTrial: partnerPackageTrialActivationSchema.nullable().optional(),
  packageTrialHistory: z.array(partnerPackageTrialActivationSchema),
  trialActivationTiers: z.array(z.string()),
  maxTrialDays: z.number(),
  updatedAt: z.string(),
})

export const partnerShopifyBillingSchema = z.object({
  mode: nullableString,
  tierKey: nullableString,
  planName: nullableString,
  status: nullableString,
  subscriptionName: nullableString,
  merchantApprovalRequired: z.boolean(),
  launchBlocked: z.boolean(),
  paidTier: z.boolean(),
  actionCapable: z.boolean(),
  allowedSurfaces: z.array(z.string()),
  recordedAt: nullableDateString,
  reason: nullableString,
})

export const partnerShopifyStorefrontPlacementSchema = z.object({
  surfaceId: z.string(),
  label: z.string(),
  blockHandle: z.string(),
  template: z.string(),
  target: z.string(),
  requiredTierKey: z.string(),
  enabled: z.boolean(),
  available: z.boolean(),
  guidance: z.string(),
})

export const partnerShopifyMerchantActionSchema = z.object({
  actionKey: z.string(),
  label: z.string(),
  owner: z.string(),
  status: z.string(),
  url: nullableString,
  reason: z.string(),
})

export const partnerShopifyActivationSchema = z.object({
  status: z.string(),
  storefrontUrl: z.string(),
  themeEditorUrl: z.string(),
  appEmbedHandle: z.string(),
  message: z.string(),
  placements: z.array(partnerShopifyStorefrontPlacementSchema),
  merchantActions: z.array(partnerShopifyMerchantActionSchema),
})

export const partnerShopifyUsageBreakdownSchema = z.object({
  key: nullableString,
  label: nullableString,
  queryText: nullableString,
  count: z.number(),
  lastAskedAt: nullableDateString,
  summary: nullableString,
})

export const partnerShopifyUsageSchema = z.object({
  status: z.string(),
  generatedAt: nullableDateString,
  lastActivityAt: nullableDateString,
  totalToday: z.number(),
  totalLast7Days: z.number(),
  todayBreakdown: z.array(partnerShopifyUsageBreakdownSchema),
  last7DayBreakdown: z.array(partnerShopifyUsageBreakdownSchema),
  surfaceUsage: z.array(partnerShopifyUsageBreakdownSchema),
  topQuestions: z.array(partnerShopifyUsageBreakdownSchema),
  surfaceJourneys: z.array(partnerShopifyUsageBreakdownSchema),
  roiStatus: nullableString,
  roiMessage: nullableString,
  roiRecommendations: z.array(z.string()),
  reason: nullableString,
})

export const partnerShopifyProvisioningJobSchema = z.object({
  id: nullableString,
  jobType: nullableString,
  status: nullableString,
  phase: nullableString,
  requestedPackageKey: nullableString,
  requestedTierKey: nullableString,
  requestedPluginIds: z.array(z.string()),
  vectorReindexRequired: z.boolean(),
  attemptCount: z.number(),
  maxAttempts: z.number(),
  lastErrorCode: nullableString,
  nextAction: nullableString,
  summaryMessage: nullableString,
  readyAt: nullableDateString,
  failedAt: nullableDateString,
  cancelledAt: nullableDateString,
  createdAt: nullableDateString,
  updatedAt: nullableDateString,
})

export const partnerShopifyProvisioningSchema = z.object({
  status: nullableString,
  phase: nullableString,
  nextAction: nullableString,
  summaryMessage: nullableString,
  effectivePackageKey: nullableString,
  effectiveTierKey: nullableString,
  latestJob: partnerShopifyProvisioningJobSchema.nullable(),
  recentJobs: z.array(partnerShopifyProvisioningJobSchema),
})

export const partnerShopifySupportReadinessSchema = z.object({
  status: z.string(),
  message: nullableString,
  lifecycleStage: nullableString,
  orderLookupSupported: z.boolean(),
  orderLookupScopeGranted: z.boolean(),
  allOrdersScopeGranted: z.boolean(),
  appScopesUpdateWebhookReady: z.boolean(),
  installRecoveryRequired: z.boolean(),
  installRecoveryUrl: nullableString,
  scopeGrantRequired: z.boolean(),
  scopeGrantUrl: nullableString,
  installStatus: nullableString,
  billingTier: nullableString,
  billingStatus: nullableString,
  grantedScopes: z.array(z.string()),
  missingScopes: z.array(z.string()),
  merchantHandoffConfigured: z.boolean(),
  merchantHandoffMessage: nullableString,
  nextActions: z.array(z.string()),
  verificationMethods: z.array(z.string()),
  supportedCapabilities: z.array(z.string()),
  blockedCapabilities: z.array(z.string()),
})

export const partnerShopifyWebhookTopicSchema = z.object({
  topic: nullableString,
  expectedName: nullableString,
  status: nullableString,
  message: nullableString,
})

export const partnerShopifyWebhookSchema = z.object({
  status: z.string(),
  message: nullableString,
  expectedCount: z.number(),
  readyCount: z.number(),
  missingCount: z.number(),
  driftedCount: z.number(),
  checkedAt: nullableDateString,
  topics: z.array(partnerShopifyWebhookTopicSchema),
})

export const partnerShopifyActionAuditSchema = z.object({
  id: z.string(),
  actionType: nullableString,
  actionPackage: nullableString,
  surfaceId: nullableString,
  pageType: nullableString,
  productHandle: nullableString,
  productTitle: nullableString,
  variantId: nullableString,
  requestedQuantity: z.number().nullable().optional(),
  resultingQuantity: z.number().nullable().optional(),
  confirmationRequired: z.boolean(),
  confirmationAccepted: z.boolean(),
  status: nullableString,
  message: nullableString,
  createdAt: nullableDateString,
  completedAt: nullableDateString,
})

export const partnerShopifyVectorizationRunSchema = z.object({
  reason: nullableString,
  status: nullableString,
  requestedStatus: nullableString,
  entityScope: z.array(z.string()),
  createdAt: nullableDateString,
  startedAt: nullableDateString,
  completedAt: nullableDateString,
  updatedAt: nullableDateString,
})

export const partnerShopifyVectorizationSourcePolicySchema = z.object({
  sourceCategory: z.string(),
  enabled: z.boolean(),
  manualIndexAllowed: z.boolean(),
  manualReindexAllowed: z.boolean(),
  autoIndexingEnabled: z.boolean(),
  createTriggerEnabled: z.boolean(),
  deleteTriggerEnabled: z.boolean(),
  updateTriggerMode: z.string(),
  selectedIndexedFields: z.array(z.string()),
  debounceWindowSeconds: z.number(),
  minimumRunIntervalSeconds: z.number(),
})

export const partnerShopifyVectorizationPolicySchema = z.object({
  policyVersion: z.number(),
  autoIndexingDefault: z.boolean(),
  sourcePolicies: z.array(partnerShopifyVectorizationSourcePolicySchema),
  updatedBy: nullableString,
  updatedAt: nullableDateString,
})

export const partnerShopifyVectorizationIndexedFieldSchema = z.object({
  fieldKey: z.string(),
  sourceCategory: z.string(),
  entityType: z.string(),
  sourceField: z.string(),
  label: z.string(),
  selectableForTriggerPolicy: z.boolean(),
})

export const partnerShopifyVectorizationAutomationSchema = z.object({
  autoIndexingHealthy: z.boolean(),
  queuedEvents: z.number(),
  leasedEvents: z.number(),
  dispatchedEvents: z.number(),
  skippedEvents: z.number(),
  failedEvents: z.number(),
  deadLetteredEvents: z.number(),
  lastAutoEventAt: nullableDateString,
  lastSuccessfulAutoIndexAt: nullableDateString,
  lastFailedAutoIndexAt: nullableDateString,
  lastAutoRunId: nullableString,
  degradedReasons: z.array(z.string()),
})

export const partnerShopifyVectorizationEventSchema = z.object({
  id: z.string(),
  sourceCategory: nullableString,
  entityType: nullableString,
  operation: nullableString,
  status: nullableString,
  triggerReason: nullableString,
  failureCode: nullableString,
  occurredAt: nullableDateString,
  queuedAt: nullableDateString,
  lastAttemptAt: nullableDateString,
  completedAt: nullableDateString,
  notes: nullableString,
})

export const partnerShopifyVectorizationOperationsSchema = z.object({
  bootstrapped: z.boolean(),
  selectedCategories: z.array(z.string()),
  selectedEntityTypes: z.array(z.string()),
  requiredPluginIds: z.array(z.string()),
  installedPluginIds: z.array(z.string()),
  missingPluginIds: z.array(z.string()),
  disabledPluginIds: z.array(z.string()),
  reconciliationRequired: z.boolean(),
  connectionConfigured: z.boolean(),
  sourceConnectionStatus: nullableString,
  planConfigured: z.boolean(),
  planStatus: nullableString,
  runnerConfigured: z.boolean(),
  runnerRegistrationStatus: nullableString,
  deploymentApplyInProgress: z.boolean(),
  deploymentApplyStatus: nullableString,
  runnerMode: nullableString,
  syncState: nullableString,
  readyToRun: z.boolean(),
  blockingReasons: z.array(z.string()),
  lastRun: partnerShopifyVectorizationRunSchema.nullable(),
  policy: partnerShopifyVectorizationPolicySchema.nullable(),
  effectiveIndexedFields: z.array(partnerShopifyVectorizationIndexedFieldSchema),
  automation: partnerShopifyVectorizationAutomationSchema.nullable(),
  recentEvents: z.array(partnerShopifyVectorizationEventSchema),
})

export const partnerShopifyOperationsSchema = z.object({
  storeId: z.string(),
  shopDomain: z.string(),
  merchantName: z.string(),
  installStatus: z.string(),
  widgetStatus: z.string(),
  knowledgeSyncStatus: z.string(),
  readinessStatus: z.string(),
  billing: partnerShopifyBillingSchema,
  activation: partnerShopifyActivationSchema,
  usage: partnerShopifyUsageSchema,
  provisioning: partnerShopifyProvisioningSchema,
  supportReadiness: partnerShopifySupportReadinessSchema,
  webhooks: partnerShopifyWebhookSchema,
  recentActions: z.array(partnerShopifyActionAuditSchema),
  vectorization: partnerShopifyVectorizationOperationsSchema.nullable(),
  capabilities: z.array(z.string()),
  checkedAt: z.string(),
})

export const partnerLaunchReadinessSchema = z.object({
  storeId: z.string(),
  shopDomain: z.string(),
  status: z.string(),
  stagingReady: z.boolean(),
  evidenceReady: z.boolean(),
  productionPromotionAllowed: z.boolean(),
  productionPromotionReady: z.boolean(),
  goLiveEligible: z.boolean(),
  blockers: z.array(z.string()),
  nextActions: z.array(z.string()),
  latestVerificationRunId: nullableString,
  latestVerificationStatus: nullableString,
  latestEvidenceBundleId: nullableString,
  latestEvidenceStatus: nullableString,
  merchantAction: z.string(),
  checkedAt: z.string(),
})

export const partnerProductionPromotionSchema = z.object({
  storeId: z.string(),
  shopDomain: z.string(),
  status: z.string(),
  message: z.string(),
  onboardingStatus: nullableString,
  latestReleaseStatus: nullableString,
  latestReleaseVerificationStatus: nullableString,
  blockers: z.array(z.string()),
  nextActions: z.array(z.string()),
  requestedAt: z.string(),
})

export const partnerActivityEventSchema = z.object({
  id: z.string(),
  action: z.string(),
  targetType: nullableString,
  targetId: nullableString,
  result: z.string(),
  details: z.record(z.unknown()),
  createdAt: z.string(),
})

export const partnerClientImplementationSchema = z.object({
  id: z.string(),
  clientName: z.string(),
  contactEmail: nullableString,
  storeConnectionId: nullableString,
  shopDomain: z.string(),
  vertical: nullableString,
  requestedTier: z.string(),
  requestedSurfaces: z.array(z.string()),
  knownIntegrations: z.array(z.string()),
  status: z.string(),
  approvalUrl: nullableString,
  approvalExpiresAt: nullableDateString,
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const partnerEligibleStoreSchema = z.object({
  storeConnectionId: z.string(),
  shopDomain: z.string(),
  displayName: z.string(),
  installStatus: z.string(),
  knowledgeSyncStatus: z.string(),
  readinessStatus: z.string(),
  widgetStatus: z.string(),
  lastActivityAt: nullableDateString,
  enabledSourceCategories: z.array(z.string()),
  enabledSurfaces: z.array(z.string()),
})

export const partnerStoreAccessLinkSchema = z.object({
  requestId: z.string(),
  implementationRequestId: z.string(),
  approvalUrl: z.string(),
  status: z.string(),
  expiresAt: z.string(),
})

export const merchantAccessRequestSchema = z.object({
  requestId: z.string(),
  implementationRequestId: z.string(),
  partnerAccountId: z.string(),
  partnerName: z.string(),
  clientName: z.string(),
  contactEmail: nullableString,
  storeConnectionId: nullableString,
  assignmentId: nullableString,
  shopDomain: z.string(),
  requestedTier: nullableString,
  requestedSurfaces: z.array(z.string()),
  knownIntegrations: z.array(z.string()),
  notes: nullableString,
  requestedScope: z.string(),
  status: z.string(),
  createdAt: z.string(),
  expiresAt: z.string(),
  approvedAt: nullableDateString,
  revokedAt: nullableDateString,
  inviteRecipientEmail: nullableString,
  inviteStatus: nullableString,
  inviteChannel: nullableString,
  inviteMessage: nullableString,
  inviteSentAt: nullableDateString,
  inviteCount: z.number(),
  updatedAt: z.string(),
})

export const merchantInviteSchema = z.object({
  requestId: z.string(),
  implementationRequestId: z.string(),
  shopDomain: z.string(),
  recipientEmail: z.string(),
  status: z.string(),
  channel: z.string(),
  message: z.string(),
  approvalUrl: z.string(),
  sentAt: nullableDateString,
  inviteCount: z.number(),
})

export const merchantApprovalSchema = z.object({
  assignmentId: z.string(),
  shopDomain: z.string(),
  status: z.string(),
  approvedAt: z.string(),
})

export const merchantDecisionSchema = z.object({
  requestId: z.string(),
  assignmentId: nullableString,
  shopDomain: z.string(),
  status: z.string(),
  decidedAt: z.string(),
})

export const merchantRollbackRequestSchema = z.object({
  storeId: z.string(),
  shopDomain: z.string(),
  status: z.string(),
  message: z.string(),
  escalationId: z.string(),
  nextActions: z.array(z.string()),
  requestedAt: z.string(),
})

export const partnerCatalogEntrySchema = z.object({
  surfaceId: z.string(),
  name: z.string(),
  tier: z.string(),
  type: z.string(),
  shopperProblem: z.string(),
  storefrontPlacement: z.string(),
  requiredSourceData: z.array(z.string()),
  merchantSetup: z.array(z.string()),
  verificationSteps: z.array(z.string()),
  healthyResult: z.string(),
  failureSigns: z.array(z.string()),
  limitations: z.array(z.string()),
  launchSafeClaim: z.string(),
  escalationEvidence: z.array(z.string()),
})

export const partnerEscalationSchema = z.object({
  id: z.string(),
  storeAssignmentId: nullableString,
  shopDomain: nullableString,
  title: z.string(),
  severity: z.string(),
  status: z.string(),
  nextAction: nullableString,
  dueAt: nullableDateString,
  description: z.string(),
  resolutionSummary: nullableString,
  evidenceBundleIds: z.array(z.string()),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const partnerReplySchema = z.object({
  id: z.string(),
  authorName: z.string(),
  authorRole: z.string(),
  visibility: z.string(),
  bodyMarkdown: z.string(),
  attachments: z.array(z.string()),
  createdAt: z.string(),
})

export const partnerThreadSchema = z.object({
  escalation: partnerEscalationSchema,
  replies: z.array(partnerReplySchema),
})

export const partnerVerificationStepSchema = z.object({
  stepId: z.string(),
  label: z.string(),
  status: z.string(),
  partnerSafeMessage: z.string(),
  suggestedFix: nullableString,
  evidence: z.array(z.string()),
  checkedAt: nullableDateString,
  sortOrder: z.number(),
})

export const partnerVerificationPackSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
  steps: z.array(partnerVerificationStepSchema),
})

export const partnerVerificationRunSchema = z.object({
  id: z.string(),
  storeAssignmentId: z.string(),
  shopDomain: nullableString,
  packId: z.string(),
  packName: z.string(),
  status: z.string(),
  totalSteps: z.number(),
  passedSteps: z.number(),
  failedSteps: z.number(),
  blockedSteps: z.number(),
  evidenceBundleId: nullableString,
  summary: z.record(z.unknown()),
  startedAt: z.string(),
  completedAt: nullableDateString,
  steps: z.array(partnerVerificationStepSchema),
})

export const partnerEvidenceBundleSchema = z.object({
  id: z.string(),
  storeAssignmentId: nullableString,
  shopDomain: nullableString,
  verificationRunId: nullableString,
  bundleName: z.string(),
  bundleKind: z.string(),
  status: z.string(),
  generatedBy: nullableString,
  summary: z.record(z.unknown()),
  attachments: z.array(z.string()),
  generatedAt: z.string(),
  expiresAt: nullableDateString,
})

export const merchantWorkspaceSchema = z.object({
  accessRequest: merchantAccessRequestSchema,
  store: partnerStoreSchema.nullable(),
  launchReadiness: partnerLaunchReadinessSchema.nullable(),
  evidenceBundles: z.array(partnerEvidenceBundleSchema),
  supportEscalations: z.array(partnerEscalationSchema),
  availableActions: z.array(z.string()),
  limitations: z.array(z.string()),
  checkedAt: z.string(),
})

export const partnerTemplateSchema = z.object({
  id: z.string(),
  name: z.string(),
  category: z.string(),
  vertical: z.string(),
  surfaceIds: z.array(z.string()),
  bodyMarkdown: z.string(),
  assumptions: z.array(z.string()),
  checklist: z.array(z.string()),
  updatedAt: z.string(),
})

export const partnerTemplateApplicationSchema = z.object({
  id: z.string(),
  templateId: z.string(),
  templateName: z.string(),
  category: z.string(),
  storeAssignmentId: nullableString,
  shopDomain: nullableString,
  status: z.string(),
  checklist: z.array(z.string()),
  assumptions: z.array(z.string()),
  appliedAt: z.string(),
  updatedAt: z.string(),
})

export const partnerStoreNoteSchema = z.object({
  id: z.string(),
  storeAssignmentId: z.string(),
  authorName: z.string(),
  bodyMarkdown: z.string(),
  status: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
})

export const partnerThinkerSessionSchema = z.object({
  id: z.string(),
  deploymentId: nullableString,
  tenantId: nullableString,
  customerId: nullableString,
  storeId: nullableString,
  shopDomain: nullableString,
  consumerId: nullableString,
  userSubject: nullableString,
  channel: z.string(),
  mode: z.string(),
  status: z.string(),
  issueCategory: z.string(),
  riskClass: z.string(),
  userQuestion: z.string(),
  userSafeAnswer: nullableString,
  sourceCitations: z.array(z.string()),
  terminalReason: nullableString,
  startedAt: z.string(),
  completedAt: nullableDateString,
  updatedAt: z.string(),
  evidenceCount: z.number(),
  recommendation: nullableString,
})

export const partnerThinkerEvidenceSchema = z.object({
  id: z.string(),
  sessionId: z.string(),
  kind: z.string(),
  sourceKind: z.string(),
  sourceIdentifier: z.string(),
  freshness: z.string(),
  confidence: z.number(),
  redactionState: z.string(),
  summary: z.string(),
  safeSummary: z.string(),
  operatorRawReference: nullableString,
  capturedAt: z.string(),
})

export const partnerThinkerPlanSchema = z.object({
  id: z.string(),
  sessionId: z.string(),
  issueSummary: z.string(),
  diagnosis: z.string(),
  optionsConsidered: z.array(z.string()),
  recommendedNextStep: z.string(),
  recommendation: z.string(),
  userExplanation: z.string(),
  escalationTarget: nullableString,
  evidenceItemIds: z.array(z.string()),
  createdAt: z.string(),
})

export const partnerResolverPolicySchema = z.object({
  id: z.string(),
  proposalId: z.string(),
  outcome: z.string(),
  riskLevel: z.string(),
  requiredScopes: z.array(z.string()),
  missingScopes: z.array(z.string()),
  tierRequirement: z.string(),
  actorRequirement: z.string(),
  dryRunRequired: z.boolean(),
  confirmationRequired: z.boolean(),
  approvalRequired: z.boolean(),
  confirmationPreview: nullableString,
  operatorReason: z.string(),
  userReason: z.string(),
  decidedAt: z.string(),
})

export const partnerResolverDryRunSchema = z.object({
  id: z.string(),
  proposalId: z.string(),
  targetAction: z.string(),
  validatedParameters: z.unknown(),
  expectedStateTransition: z.string(),
  expectedSideEffects: z.array(z.string()),
  warnings: z.array(z.string()),
  unsupportedFields: z.array(z.string()),
  idempotencyPosture: z.string(),
  rollbackPosture: z.string(),
  evidenceFreshness: z.string(),
  productBoundary: z.string(),
  status: z.string(),
  createdAt: z.string(),
})

export const partnerResolverExecutionSchema = z.object({
  id: z.string(),
  proposalId: z.string(),
  dryRunId: nullableString,
  idempotencyKey: z.string(),
  actionFamily: z.string(),
  status: z.string(),
  productBoundary: z.string(),
  executionResult: z.unknown(),
  verificationStatus: z.string(),
  recoveryGuidance: nullableString,
  createdAt: z.string(),
  completedAt: nullableDateString,
})

export const partnerResolverProposalSchema = z.object({
  id: z.string(),
  sessionId: z.string(),
  actionId: z.string(),
  actionFamily: z.string(),
  targetDomain: z.string(),
  productBoundary: z.string(),
  actorContext: z.string(),
  tenantId: nullableString,
  storeId: nullableString,
  deploymentId: nullableString,
  redactedParameters: z.unknown(),
  operatorParameters: z.unknown().nullable().optional(),
  sourceEvidenceItemIds: z.array(z.string()),
  proposalText: z.string(),
  normalizedIntent: z.unknown(),
  status: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  latestPolicyDecision: partnerResolverPolicySchema.nullable(),
  latestDryRun: partnerResolverDryRunSchema.nullable(),
  executions: z.array(partnerResolverExecutionSchema),
})

export const partnerThinkerAuditSchema = z.object({
  id: z.string(),
  sessionId: z.string(),
  eventType: z.string(),
  actorId: z.string(),
  actorRole: z.string(),
  details: z.unknown(),
  createdAt: z.string(),
})

export const partnerThinkerSessionDetailSchema = z.object({
  session: partnerThinkerSessionSchema,
  evidence: z.array(partnerThinkerEvidenceSchema),
  plan: partnerThinkerPlanSchema.nullable(),
  auditEvents: z.array(partnerThinkerAuditSchema),
  resolverProposals: z.array(partnerResolverProposalSchema),
})

export const partnerThinkerEscalationResultSchema = z.object({
  supportEscalationId: z.string(),
  evidenceBundleId: z.string(),
  status: z.string(),
})

export type PartnerSession = z.infer<typeof partnerSessionSchema>
export type PartnerMember = z.infer<typeof partnerMemberSchema>
export type PartnerStore = z.infer<typeof partnerStoreSchema>
export type PartnerProductControl = z.infer<typeof partnerProductControlSchema>
export type PartnerShopifyOperations = z.infer<typeof partnerShopifyOperationsSchema>
export type PartnerShopifyVectorizationSourcePolicy = z.infer<typeof partnerShopifyVectorizationSourcePolicySchema>
export type PartnerLaunchReadiness = z.infer<typeof partnerLaunchReadinessSchema>
export type PartnerProductionPromotion = z.infer<typeof partnerProductionPromotionSchema>
export type PartnerProductPackage = z.infer<typeof partnerProductPackageSchema>
export type PartnerProductWidgetSettings = z.infer<typeof partnerProductWidgetSettingsSchema>
export type PartnerProductSupportProfile = z.infer<typeof partnerProductSupportProfileSchema>
export type PartnerProductSourceSettings = z.infer<typeof partnerProductSourceSettingsSchema>
export type PartnerActivityEvent = z.infer<typeof partnerActivityEventSchema>
export type PartnerClientImplementation = z.infer<typeof partnerClientImplementationSchema>
export type PartnerEligibleStore = z.infer<typeof partnerEligibleStoreSchema>
export type PartnerStoreAccessLink = z.infer<typeof partnerStoreAccessLinkSchema>
export type MerchantApproval = z.infer<typeof merchantApprovalSchema>
export type MerchantDecision = z.infer<typeof merchantDecisionSchema>
export type MerchantWorkspace = z.infer<typeof merchantWorkspaceSchema>
export type MerchantRollbackRequest = z.infer<typeof merchantRollbackRequestSchema>
export type MerchantInvite = z.infer<typeof merchantInviteSchema>
export type PartnerCatalogEntry = z.infer<typeof partnerCatalogEntrySchema>
export type PartnerEscalation = z.infer<typeof partnerEscalationSchema>
export type PartnerThread = z.infer<typeof partnerThreadSchema>
export type PartnerVerificationPack = z.infer<typeof partnerVerificationPackSchema>
export type PartnerVerificationRun = z.infer<typeof partnerVerificationRunSchema>
export type PartnerVerificationStep = z.infer<typeof partnerVerificationStepSchema>
export type PartnerEvidenceBundle = z.infer<typeof partnerEvidenceBundleSchema>
export type PartnerTemplate = z.infer<typeof partnerTemplateSchema>
export type PartnerTemplateApplication = z.infer<typeof partnerTemplateApplicationSchema>
export type PartnerStoreNote = z.infer<typeof partnerStoreNoteSchema>
export type PartnerThinkerSession = z.infer<typeof partnerThinkerSessionSchema>
export type PartnerThinkerEvidence = z.infer<typeof partnerThinkerEvidenceSchema>
export type PartnerThinkerSessionDetail = z.infer<typeof partnerThinkerSessionDetailSchema>
export type PartnerThinkerEscalationResult = z.infer<typeof partnerThinkerEscalationResultSchema>
