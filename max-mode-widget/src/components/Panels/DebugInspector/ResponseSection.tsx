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
  return (
    <div className="space-y-2 border-t border-gray-200 dark:border-gray-700 pt-3">
      <ResponseHeader debugResponse={debugResponse} />

      {debugResponse &&
        (() => {
          const result = debugResponse.data?.result;
          const resultData = result?.data;
          const metadata = result?.metadata;

          const requiresRetrieval = resultData?.requiresRetrieval === true;
          const retrievalSkipped = resultData?.metadata?.retrievalSkipped === true;
          const retrievalSkipReason = resultData?.metadata?.retrievalSkipReason;
          const hasRagResponse = resultData?.ragResponse != null;
          const hasDocuments = resultData?.documents && resultData.documents.length > 0;
          const ragExecuted = hasRagResponse || hasDocuments;

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

              <RawResultJsonPreview result={result} onExpandJson={onExpandJson} />
            </div>
          );
        })()}
    </div>
  );
}

