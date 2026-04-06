import { Info } from "lucide-react";

import type { ChatMessage, DebugData } from "../../../types";

import { RequestSection } from "./RequestSection";
import { ResponseSection } from "./ResponseSection";

export function DebugInspectorContent({
  selectedDebugMessage,
  lastRequestData,
  lastResponseData,
  isQueryExpanded,
  setIsQueryExpanded,
  onExpandJson,
}: {
  selectedDebugMessage: ChatMessage | null;
  lastRequestData: DebugData["request"] | null;
  lastResponseData: DebugData["response"] | null;
  isQueryExpanded: boolean;
  setIsQueryExpanded: (expanded: boolean) => void;
  onExpandJson: () => void;
}) {
  const debugRequest = selectedDebugMessage?.debugData?.request || lastRequestData;
  const debugResponse = selectedDebugMessage?.debugData?.response || lastResponseData;

  if (!debugRequest && !debugResponse) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-center py-8">
        <div className="h-12 w-12 rounded-full bg-gray-100 dark:bg-gray-800 flex items-center justify-center mb-3">
          <Info className="h-6 w-6 text-gray-400" />
        </div>
        <h3 className="text-sm font-semibold text-gray-600 dark:text-gray-300">No API Calls Yet</h3>
        <p className="text-xs text-gray-500 mt-1">Send a message to see details here.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <RequestSection debugRequest={debugRequest} />
      <ResponseSection
        debugRequest={debugRequest}
        debugResponse={debugResponse}
        isQueryExpanded={isQueryExpanded}
        setIsQueryExpanded={setIsQueryExpanded}
        onExpandJson={onExpandJson}
      />
    </div>
  );
}
