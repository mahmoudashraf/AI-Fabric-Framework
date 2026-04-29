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

export const partnerProductWidgetSettingsSchema = z.object({
  launcherLabel: z.string(),
  welcomeMessage: z.string(),
  shellModeProfile: z.string(),
  enabledSurfaces: z.array(z.string()),
  defaultConversationMode: z.string(),
  allowedConversationModes: z.array(z.string()),
  pageModeMappings: z.record(z.string()),
})

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
  updatedAt: z.string(),
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

export const merchantApprovalSchema = z.object({
  assignmentId: z.string(),
  shopDomain: z.string(),
  status: z.string(),
  approvedAt: z.string(),
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

export type PartnerSession = z.infer<typeof partnerSessionSchema>
export type PartnerMember = z.infer<typeof partnerMemberSchema>
export type PartnerStore = z.infer<typeof partnerStoreSchema>
export type PartnerProductControl = z.infer<typeof partnerProductControlSchema>
export type PartnerProductPackage = z.infer<typeof partnerProductPackageSchema>
export type PartnerProductWidgetSettings = z.infer<typeof partnerProductWidgetSettingsSchema>
export type PartnerProductSupportProfile = z.infer<typeof partnerProductSupportProfileSchema>
export type PartnerProductSourceSettings = z.infer<typeof partnerProductSourceSettingsSchema>
export type PartnerActivityEvent = z.infer<typeof partnerActivityEventSchema>
export type PartnerClientImplementation = z.infer<typeof partnerClientImplementationSchema>
export type PartnerEligibleStore = z.infer<typeof partnerEligibleStoreSchema>
export type PartnerStoreAccessLink = z.infer<typeof partnerStoreAccessLinkSchema>
export type MerchantApproval = z.infer<typeof merchantApprovalSchema>
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
