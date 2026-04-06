import { CheckCircle2 } from "lucide-react";

import type { DebugData } from "../../../types";

export function ResponseHeader({ debugResponse }: { debugResponse: DebugData["response"] | null }) {
  return (
    <div className="flex items-center gap-2">
      <div className="h-6 w-6 rounded-lg bg-green-500/10 flex items-center justify-center">
        <CheckCircle2 className="h-3 w-3 text-green-600" />
      </div>
      <h3 className="text-sm font-bold text-gray-800 dark:text-gray-200">Response</h3>
      <div className="flex items-center gap-2 ml-auto">
        {debugResponse?.durationMs != null && (
          <span
            className={`px-1.5 py-0.5 text-[10px] font-bold rounded ${
              debugResponse.durationMs < 1000
                ? "bg-green-500/20 text-green-700 dark:text-green-300"
                : debugResponse.durationMs < 3000
                  ? "bg-yellow-500/20 text-yellow-700 dark:text-yellow-300"
                  : "bg-red-500/20 text-red-700 dark:text-red-300"
            }`}
          >
            {debugResponse.durationMs < 1000 ? `${debugResponse.durationMs}ms` : `${(debugResponse.durationMs / 1000).toFixed(2)}s`}
          </span>
        )}
        {debugResponse?.timestamp && (
          <span className="text-[10px] text-gray-500">{new Date(debugResponse.timestamp).toLocaleTimeString()}</span>
        )}
      </div>
    </div>
  );
}

