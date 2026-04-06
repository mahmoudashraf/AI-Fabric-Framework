export interface MaxModeProps {
  isOpen: boolean;
  onClose: () => void;
}

export type AttachedItem = { type: string; data: any };

export interface Product {
  id: string;
  sku: string;
  name: string;
  description: string;
  price: number;
  category?: string;
  inStockQty?: number;
}

export type ResultType =
  | "ACTION_EXECUTED"
  | "ACTION_DENIED"
  | "INFORMATION_PROVIDED"
  | "CONFIRMATION_REQUIRED"
  | "CLARIFICATION_REQUIRED"
  | "OUT_OF_SCOPE"
  | "COMPOUND_HANDLED"
  | "ERROR";

export interface SanitizedPayload {
  type: ResultType;
  success: boolean;
  message: string;
  data?: any;
  safeSummary?: string;
  sanitization?: {
    risk: string;
    detectedTypes: string[];
  };
}

export interface SmartSuggestion {
  intent: string;
  title: string;
  query: string;
  confidence: number;
  priority?: string;
  rationale: string;
  response?: string;
  documents?: any[];
  metadata?: any;
}

export interface NextStep {
  intent: string;
  query: string;
  rationale: string;
  confidence: number;
  vectorSpace?: string;
}

export interface ChatResult {
  type: ResultType;
  success: boolean;
  smartSuggestion?: SmartSuggestion;
  nextSteps?: NextStep[];
  sanitizedPayload: SanitizedPayload;
}

export interface DebugData {
  request: {
    endpoint: string;
    method: string;
    timestamp: string;
    payload: any;
  };
  response: {
    timestamp: string;
    status: number;
    data: any;
    durationMs?: number;
  };
}

export interface ChatMessage {
  id: string;
  type: "user" | "ai";
  content: string;
  timestamp: string;
  result?: ChatResult;
  resultType?: ResultType;
  attachedItems?: Array<{ type: string; data: any }>;
  documents?: Document[];
  debugData?: DebugData;
  searchCategory?: string;
}

export interface Document {
  id: string;
  title: string;
  content: string;
  type: string;
  metadata?: any;
  messageId?: string;
  similarity?: number;
  score?: number;
}

export interface Conversation {
  id: string;
  ownerId: string;
  title?: string;
  status?: string;
  createdAt: string;
  lastInteractionAt?: string;
  turnsCount?: number;
}

export interface ConversationTurn {
  timestamp: string;
  userQuery: string;
  aiResponse: string;
}

export interface ConversationDetail extends Conversation {
  turns: ConversationTurn[];
}

