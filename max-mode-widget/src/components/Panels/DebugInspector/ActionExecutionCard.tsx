export function ActionExecutionCard({ resultType, resultData }: { resultType: string; resultData: any }) {
  const colorMap: Record<string, string> = {
    ACTION_EXECUTED: "bg-green-50 dark:bg-green-900/20 border-green-200",
    CONFIRMATION_REQUIRED: "bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200",
    CLARIFICATION_REQUIRED: "bg-orange-50 dark:bg-orange-900/20 border-orange-200",
    COMPOUND_HANDLED: "bg-purple-50 dark:bg-purple-900/20 border-purple-200",
    ACTION_DENIED: "bg-red-50 dark:bg-red-900/20 border-red-200",
  };

  return (
    <div className={`rounded-lg p-2 border ${colorMap[resultType] || "bg-red-50 dark:bg-red-900/20 border-red-200"}`}>
      <h4 className="text-[10px] font-bold uppercase mb-1">Action</h4>
      <div className="grid grid-cols-3 gap-1 text-[9px]">
        <div>
          <span className="text-gray-500">Name:</span> <span className="font-medium">{resultData?.action || "—"}</span>
        </div>
        <div>
          <span className="text-gray-500">Confirm:</span>{" "}
          <span className="font-medium">{resultData?.confirmationRequired ? "Yes" : "No"}</span>
        </div>
        {resultData?.actionResult && (
          <div>
            <span className="text-gray-500">Result:</span>{" "}
            <span className={`font-medium ${resultData.actionResult.success ? "text-green-600" : "text-red-600"}`}>
              {resultData.actionResult.success ? "OK" : resultData.actionResult.errorCode}
            </span>
          </div>
        )}
      </div>
      {resultData?.confirmationMessage && <div className="mt-1 text-[9px] text-gray-600 italic truncate">{resultData.confirmationMessage}</div>}

      {resultType === "COMPOUND_HANDLED" && resultData?.results && Array.isArray(resultData.results) && (
        <div className="mt-2 space-y-1.5 border-t border-gray-200 dark:border-gray-600 pt-2">
          <span className="text-[9px] font-bold text-gray-600 dark:text-gray-400 uppercase">Sub-Results ({resultData.results.length})</span>
          {resultData.results.map((sub: any, idx: number) => (
            <div
              key={idx}
              className={`rounded p-1.5 text-[9px] border ${
                sub.type === "ACTION_EXECUTED" ? "bg-green-50/50 dark:bg-green-900/10 border-green-200/50"
                : sub.type === "CLARIFICATION_REQUIRED" ? "bg-orange-50/50 dark:bg-orange-900/10 border-orange-200/50"
                : sub.type === "CONFIRMATION_REQUIRED" ? "bg-yellow-50/50 dark:bg-yellow-900/10 border-yellow-200/50"
                : sub.type === "ACTION_DENIED" ? "bg-red-50/50 dark:bg-red-900/10 border-red-200/50"
                : "bg-gray-50/50 dark:bg-gray-800/50 border-gray-200/50"
              }`}
            >
              <div className="flex items-center gap-1.5">
                <span className={`px-1 py-0.5 rounded font-bold text-[8px] ${
                  sub.success ? "bg-green-500/20 text-green-700" : "bg-red-500/20 text-red-700"
                }`}>
                  {sub.success ? "OK" : "ERR"}
                </span>
                <span className="font-bold">{sub.type?.replace(/_/g, " ")}</span>
                {sub.data?.action && <span className="text-gray-500">({sub.data.action})</span>}
              </div>
              {sub.message && <div className="mt-0.5 text-gray-600 dark:text-gray-400 truncate">{sub.message}</div>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

