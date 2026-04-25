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

export const partnerStoreSchema = z.object({
  id: z.string(),
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

export type PartnerSession = z.infer<typeof partnerSessionSchema>
export type PartnerStore = z.infer<typeof partnerStoreSchema>
export type PartnerClientImplementation = z.infer<typeof partnerClientImplementationSchema>
export type PartnerEligibleStore = z.infer<typeof partnerEligibleStoreSchema>
export type PartnerStoreAccessLink = z.infer<typeof partnerStoreAccessLinkSchema>
export type MerchantApproval = z.infer<typeof merchantApprovalSchema>
export type PartnerCatalogEntry = z.infer<typeof partnerCatalogEntrySchema>
export type PartnerEscalation = z.infer<typeof partnerEscalationSchema>
export type PartnerThread = z.infer<typeof partnerThreadSchema>
