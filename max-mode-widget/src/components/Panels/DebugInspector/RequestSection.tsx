import { ArrowRight } from "lucide-react";

import type { DebugData } from "../../../types";

export function RequestSection({ debugRequest }: { debugRequest: DebugData["request"] | null }) {
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2">
        <div className="h-6 w-6 rounded-lg bg-blue-500/10 flex items-center justify-center">
          <ArrowRight className="h-3 w-3 text-blue-600" />
        </div>
        <h3 className="text-sm font-bold text-gray-800 dark:text-gray-200">Request</h3>
        {debugRequest?.timestamp && (
          <span className="text-[10px] text-gray-500 ml-auto">{new Date(debugRequest.timestamp).toLocaleTimeString()}</span>
        )}
      </div>

      {debugRequest && (
        <div className="space-y-2">
          <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
            <div className="flex items-center gap-2">
              <span className="px-1.5 py-0.5 text-[10px] font-bold bg-green-500 text-white rounded">POST</span>
              <span className="text-[10px] text-gray-500 truncate">{debugRequest.endpoint}</span>
            </div>
          </div>

          <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
            <h4 className="text-[10px] font-semibold text-gray-500 uppercase mb-1">Query</h4>
            <p className="text-xs text-gray-800 dark:text-gray-200">{debugRequest.payload?.query}</p>
          </div>

          <div className="grid grid-cols-2 gap-1.5">
            <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
              <h4 className="text-[9px] font-semibold text-gray-500 uppercase mb-0.5">Position</h4>
              <span
                className={`inline-block px-1.5 py-0.5 text-[10px] font-bold rounded ${
                  debugRequest.payload?.position === "cart"
                    ? "bg-orange-500 text-white"
                    : debugRequest.payload?.position === "search"
                      ? "bg-blue-500 text-white"
                      : debugRequest.payload?.position === "catalog"
                        ? "bg-indigo-500 text-white"
                        : "bg-green-500 text-white"
                }`}
              >
                {debugRequest.payload?.position || "landing"}
              </span>
            </div>
            <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
              <h4 className="text-[9px] font-semibold text-gray-500 uppercase mb-0.5">Mode</h4>
              <span
                className={`inline-block px-1.5 py-0.5 text-[10px] font-bold rounded ${
                  debugRequest.payload?.mode === "executor"
                    ? "bg-amber-500 text-white"
                    : debugRequest.payload?.mode === "cart_assistant"
                      ? "bg-emerald-500 text-white"
                      : debugRequest.payload?.mode === "navigator_deep"
                        ? "bg-purple-500 text-white"
                        : "bg-indigo-500 text-white"
                }`}
              >
                {debugRequest.payload?.mode || "—"}
              </span>
            </div>
          </div>

          {debugRequest.payload?.attachments && debugRequest.payload.attachments.length > 0 && (
            <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
              <h4 className="text-[10px] font-semibold text-gray-500 uppercase mb-1">
                Attachments ({debugRequest.payload.attachments.length})
              </h4>
              <div className="space-y-0.5 max-h-20 overflow-auto">
                {debugRequest.payload.attachments.map((att: any, idx: number) => (
                  <div key={idx} className="text-[10px] bg-white dark:bg-gray-700 rounded px-1.5 py-1">
                    <div className="flex items-center gap-1">
                      <span className="px-1 py-0.5 bg-purple-100 dark:bg-purple-900 text-purple-600 dark:text-purple-300 rounded text-[8px] font-medium">
                        {att.vectorSpace}
                      </span>
                      <span className="text-gray-600 dark:text-gray-300 truncate text-[9px]">{att.id}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          <details className="bg-gray-50 dark:bg-gray-800 rounded-lg">
            <summary className="px-2 py-1.5 cursor-pointer text-[10px] font-semibold text-gray-500 uppercase hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg">
              Full Request Payload
            </summary>
            <pre className="px-2 pb-2 text-[8px] overflow-auto max-h-32 text-gray-700 dark:text-gray-300 font-mono">
              {JSON.stringify(debugRequest.payload, null, 2)}
            </pre>
          </details>
        </div>
      )}
    </div>
  );
}

