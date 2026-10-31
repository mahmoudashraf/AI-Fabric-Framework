export type Role = "OWNER" | "SUPPORT" | "REVIEWER";
export interface Binding {
  bindingId: string;
  customerId: string;
  deploymentId: string;
  targetProfileId: string;
  deploymentName: string;
  environment: string;
  roles: Role[];
}
export interface Session {
  authenticated: boolean;
  operator?: {
    id: string;
    displayName: string;
    email: string;
    agentReference?: string;
  } | null;
  bindings: Binding[];
  registered?: boolean;
  emailVerified?: boolean;
  agentReference?: string;
  csrfToken?: string;
  csrfHeaderName?: string;
}
export interface DeskConfig {
  supabaseUrl?: string;
  supabasePublishableKey?: string;
  loginUrl?: string;
  identityPortalUrl?: string;
  oidcEnabled?: boolean;
  registrationEnabled?: boolean;
  csrfToken?: string;
  csrfHeaderName?: string;
}
export interface Conversation {
  handoverId: string;
  externalConversationRef: string;
  status: string;
  safeCustomerLabel: string;
  claimedBy?: string | null;
  claimedDisplayName?: string | null;
  unreadCount: number;
  createdAt: string;
  updatedAt: string;
}
export interface Message {
  messageId: string;
  participantRole: string;
  actorRef?: string;
  safeActorDisplayName?: string;
  content: string;
  attachmentRefs?: string[];
  createdAt: string;
}
export interface ConversationDetail {
  conversation: Conversation;
  consentedIntake: Record<string, unknown>;
  messages: Message[];
}
export interface CommandResult {
  accepted: boolean;
  outcome: string;
  conversation?: ConversationDetail;
}
export interface CallbackOperation {
  callbackId: string;
  handoverId: string;
  eventType: string;
  status: string;
  attemptCount: number;
  failureCode?: string;
  createdAt: string;
  updatedAt: string;
  retryEligible: boolean;
}
export interface Agent {
  agentId: string;
  displayName?: string;
  email?: string;
  agentReference?: string;
  roles: Role[];
  createdAt?: string;
}
export type ReviewTaskStatus =
  | "WAITING_FOR_REVIEW"
  | "DECIDING"
  | "WAITING_FOR_INFORMATION"
  | "APPROVED"
  | "REJECTED"
  | "CORRECTED"
  | "ESCALATED"
  | "EXPIRED"
  | "FAILED";
export type ReviewDecisionType =
  | "APPROVE"
  | "REJECT"
  | "CORRECT"
  | "REQUEST_INFORMATION"
  | "ESCALATE";
export type ReviewType =
  | "OPERATIONAL_REVIEW"
  | "CORRECTION"
  | "ESCALATION"
  | "OUTCOME_REVIEW"
  | "QUALITY_SAMPLE";
export interface ReviewTask {
  taskId: string;
  type: ReviewType;
  title: string;
  summary: string;
  status: ReviewTaskStatus;
  version: number;
  allowedDecisions: ReviewDecisionType[];
  policyId: { name: string; version: string };
  createdAt: string;
  expiresAt: string;
}
export interface ReviewOutcome {
  actionName: string;
  message: string;
  data: Record<string, unknown>;
}
export interface ReviewDetail {
  task: ReviewTask;
  requestedInformation: unknown;
  suppliedInformation: unknown;
  message: string | null;
  outcome: ReviewOutcome | null;
  successorTaskId: string | null;
  failureReason: string | null;
}
export interface ReviewDecisionFailure {
  reason: string;
  publicMessage: string;
  retryable: boolean;
}
export interface DecisionResult {
  task: ReviewTask | null;
  outcome: ReviewOutcome | null;
  successorTaskId: string | null;
  failure: ReviewDecisionFailure | null;
}
