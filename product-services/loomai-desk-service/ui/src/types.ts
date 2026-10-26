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
export interface ReviewTask {
  taskId: string;
  type: string;
  title: string;
  summary: string;
  status: string;
  version: number;
  allowedDecisions: ("APPROVE" | "REJECT")[];
  policyId?: { name: string; version: string };
  createdAt: string;
  expiresAt?: string;
}
export interface ReviewOutcome {
  actionName?: string;
  status?: string;
  success?: boolean;
  message?: string;
  [key: string]: unknown;
}
export interface ReviewDetail {
  task: ReviewTask;
  message?: string;
  failureReason?: string;
  proposal?: Record<string, unknown>;
  requestedInformation?: unknown;
  suppliedInformation?: unknown;
  successorTaskId?: string | null;
  outcome?: ReviewOutcome | null;
}
export interface DecisionResult {
  task?: ReviewTask | null;
  outcome?: ReviewOutcome | null;
  message?: string;
  failureReason?: string;
}
