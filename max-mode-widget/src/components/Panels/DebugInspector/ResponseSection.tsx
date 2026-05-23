import type { DebugData } from "../../../types";

import { ActionExecutionCard } from "./ActionExecutionCard";
import { AttachmentsTargetRow } from "./AttachmentsTargetRow";
import { CoreStatusRow } from "./CoreStatusRow";
import { ExtractionDiagnosticsCard } from "./ExtractionDiagnosticsCard";
import { IntentChatRow } from "./IntentChatRow";
import { OrchestrationPolicyCard } from "./OrchestrationPolicyCard";
import { RagStatusCard } from "./RagStatusCard";
import { RawResultJsonPreview } from "./RawResultJsonPreview";
import { ResponseHeader } from "./ResponseHeader";
import { SmartSuggestionCard } from "./SmartSuggestionCard";
import { VectorSpaceRoutingCard } from "./VectorSpaceRoutingCard";

export function ResponseSection({
  debugRequest,
  debugResponse,
  isQueryExpanded,
  setIsQueryExpanded,
  onExpandJson,
}: {
  debugRequest: DebugData["request"] | null;
  debugResponse: DebugData["response"] | null;
  isQueryExpanded: boolean;
  setIsQueryExpanded: (expanded: boolean) => void;
  onExpandJson: () => void;
}) {
  const responseView = buildResponseDebugView(debugResponse?.data);

  return (
    <div className="space-y-2 border-t border-gray-200 dark:border-gray-700 pt-3">
      <ResponseHeader debugResponse={debugResponse} />

      {debugResponse &&
        (() => {
          const result = responseView.result;
          const resultData = responseView.resultData;
          const metadata = responseView.metadata;

          const requiresRetrieval =
            resultData?.requiresRetrieval === true ||
            responseView.diagnostics?.upstreamSourcesCount > 0 ||
            responseView.diagnostics?.extractedSourcesCount > 0;
          const retrievalSkipped = resultData?.metadata?.retrievalSkipped === true || metadata?.retrievalSkipped === true;
          const retrievalSkipReason = resultData?.metadata?.retrievalSkipReason || metadata?.retrievalSkipReason;
          const hasRagResponse = resultData?.ragResponse != null;
          const hasDocuments = Array.isArray(resultData?.documents) && resultData.documents.length > 0;
          const ragExecuted = hasRagResponse || hasDocuments || responseView.diagnostics?.upstreamSourcesCount > 0;

          return (
            <div className="space-y-2">
              <CoreStatusRow result={result} metadata={metadata} />

              <RagStatusCard
                debugRequest={debugRequest}
                resultData={resultData}
                metadata={metadata}
                ragExecuted={ragExecuted}
                requiresRetrieval={requiresRetrieval}
                retrievalSkipped={retrievalSkipped}
                retrievalSkipReason={retrievalSkipReason}
                hasRagResponse={hasRagResponse}
                isQueryExpanded={isQueryExpanded}
                setIsQueryExpanded={setIsQueryExpanded}
              />

              {metadata?.orchestrationPolicy && <OrchestrationPolicyCard orchestrationPolicy={metadata.orchestrationPolicy} />}
              {metadata?.extractionDiagnostics && <ExtractionDiagnosticsCard extractionDiagnostics={metadata.extractionDiagnostics} />}

              <IntentChatRow intentMetadata={metadata?.intentMetadata} chat={metadata?.chat} />
              <AttachmentsTargetRow
                attachments={metadata?.attachments}
                attachmentsPrompt={metadata?.attachmentsPrompt}
                targetResolution={metadata?.targetResolution}
              />

              {metadata?.vectorSpaceRouting && metadata.vectorSpaceRouting.length > 0 && (
                <VectorSpaceRoutingCard vectorSpaceRouting={metadata.vectorSpaceRouting} />
              )}

              {(result?.type === "ACTION_EXECUTED" ||
                result?.type === "CONFIRMATION_REQUIRED" ||
                result?.type === "CLARIFICATION_REQUIRED" ||
                result?.type === "COMPOUND_HANDLED" ||
                result?.type === "ACTION_DENIED") && <ActionExecutionCard resultType={result.type} resultData={resultData} />}

              {(result?.smartSuggestion || result?.data?.smartSuggestion || result?.nextSteps) && (
                <SmartSuggestionCard
                  smartSuggestion={result?.smartSuggestion || result?.data?.smartSuggestion}
                  nextSteps={result?.nextSteps}
                />
              )}

              <RawResultJsonPreview result={responseView.rawPreview} onExpandJson={onExpandJson} />
            </div>
          );
        })()}
    </div>
  );
}

function buildResponseDebugView(data: any): {
  result: any;
  resultData: any;
  metadata: any;
  diagnostics: any;
  rawPreview: any;
} {
  const canonical = isObject(data) ? data : {};
  const bridgeDebug = isObject(canonical.debug) ? canonical.debug : {};
  const diagnostics = isObject(bridgeDebug.diagnostics) ? bridgeDebug.diagnostics : {};
  const normalizedRequest = isObject(bridgeDebug.normalizedRequest) ? bridgeDebug.normalizedRequest : undefined;
  const upstreamResponse = isObject(bridgeDebug.upstreamResponse) ? bridgeDebug.upstreamResponse : {};
  const upstreamResult = isObject(upstreamResponse.result) ? upstreamResponse.result : undefined;
  const legacyResult = isObject(canonical.result) ? canonical.result : undefined;
  const result: any = legacyResult || upstreamResult || {
    type: canonical.type || "INFORMATION_PROVIDED",
    success: typeof canonical.success === "boolean" ? canonical.success : true,
    message: canonical.safeSummary || canonical.answer || canonical.message,
    errorCode: canonical.errorCode || canonical.fallbackReason,
  };

  const legacyData = isObject(legacyResult?.data) ? legacyResult.data : {};
  const upstreamData = isObject(upstreamResult?.data) ? upstreamResult.data : {};
  const sanitizedData = isObject(upstreamResult?.sanitizedPayload?.data) ? upstreamResult.sanitizedPayload.data : {};
  const firstAction = Array.isArray(canonical.actions) && canonical.actions.length === 1 ? canonical.actions[0] : undefined;
  const canonicalActionsData =
    isObject(firstAction)
      ? firstAction
      : Array.isArray(canonical.actions) && canonical.actions.length > 1
        ? { actions: canonical.actions }
        : {};
  const canonicalDocuments = Array.isArray(canonical.sources) ? canonical.sources : [];
  const canonicalRagResponse = isObject(canonical.ragResponse) ? canonical.ragResponse : undefined;
  const upstreamDocuments = firstArray(
    upstreamData.documents,
    upstreamData.ragResponse?.documents,
    sanitizedData.documents,
    sanitizedData.ragResponse?.documents,
  );
  const documents = firstNonEmptyArray(
    legacyData.documents,
    legacyData.ragResponse?.documents,
    canonicalRagResponse?.documents,
    upstreamDocuments,
    canonicalDocuments,
  );
  const ragResponse = firstObject(
    canonicalRagResponse,
    legacyData.ragResponse,
    upstreamData.ragResponse,
    sanitizedData.ragResponse,
    documents.length > 0
      ? {
          query: normalizedRequest?.query || canonical.query,
          optimizedQuery: upstreamData.metadata?.embeddingQuery || upstreamResult?.metadata?.embeddingQuery,
          entityType: documents[0]?.type || documents[0]?.source || documents[0]?.metadata?.vectorSpace || "document",
          usedDocuments: documents.length,
          processingTimeMs: upstreamData.ragResponse?.processingTimeMs,
          documents,
        }
      : undefined,
  );

  const resultData = {
    ...sanitizedData,
    ...upstreamData,
    ...legacyData,
    ...canonicalActionsData,
    ...(documents.length > 0 ? { documents } : {}),
    ...(ragResponse ? { ragResponse } : {}),
  };
  const metadata: Record<string, any> = {
    ...(isObject(upstreamResult?.metadata) ? upstreamResult.metadata : {}),
    ...(isObject(upstreamData.metadata) ? upstreamData.metadata : {}),
    ...(isObject(legacyResult?.metadata) ? legacyResult.metadata : {}),
    ...(isObject(canonical.metadata) ? canonical.metadata : {}),
    ...(canonical.providerRequestId ? { requestId: canonical.providerRequestId } : {}),
    ...(diagnostics.providerRequestId ? { requestId: diagnostics.providerRequestId } : {}),
    ...(Object.keys(diagnostics).length > 0 ? { diagnostics } : {}),
  };

  return {
    result: {
      ...result,
      type: result.type || canonical.type || "INFORMATION_PROVIDED",
      success: typeof result.success === "boolean"
        ? result.success
        : typeof canonical.success === "boolean"
          ? canonical.success
          : true,
      errorCode: result.errorCode || canonical.errorCode || canonical.fallbackReason || resultData.errorCode,
      data: resultData,
    },
    resultData,
    metadata,
    diagnostics,
    rawPreview: Object.keys(bridgeDebug).length > 0 ? bridgeDebug : legacyResult || canonical,
  };
}

function isObject(value: any): value is Record<string, any> {
  return value != null && typeof value === "object" && !Array.isArray(value);
}

function firstArray(...values: any[]): any[] {
  for (const value of values) {
    if (Array.isArray(value)) {
      return value;
    }
  }
  return [];
}

function firstNonEmptyArray(...values: any[]): any[] {
  for (const value of values) {
    if (Array.isArray(value) && value.length > 0) {
      return value;
    }
  }
  return [];
}

function firstObject(...values: any[]): any | undefined {
  for (const value of values) {
    if (isObject(value)) {
      return value;
    }
  }
  return undefined;
}
