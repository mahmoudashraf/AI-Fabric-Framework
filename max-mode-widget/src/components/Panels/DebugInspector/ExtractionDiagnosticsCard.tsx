export function ExtractionDiagnosticsCard({
  extractionDiagnostics,
}: {
  extractionDiagnostics: any;
}) {
  return (
    <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
      <h4 className="text-[10px] font-bold text-gray-600 dark:text-gray-400 uppercase mb-1">Extraction</h4>
      <div className="grid grid-cols-4 gap-1 text-[9px] mb-1">
        <div className="text-center bg-white dark:bg-gray-700 rounded p-1">
          <div className="font-bold text-gray-700 dark:text-gray-300">{extractionDiagnostics.extractionPath}</div>
          <div className="text-gray-500">Path</div>
        </div>
        <div className="text-center bg-white dark:bg-gray-700 rounded p-1">
          <div className="font-bold text-gray-700 dark:text-gray-300">{extractionDiagnostics.extractionAttempts}</div>
          <div className="text-gray-500">Attempts</div>
        </div>
        <div className="text-center bg-white dark:bg-gray-700 rounded p-1">
          <div className="font-bold text-gray-700 dark:text-gray-300">{extractionDiagnostics.llmCalls}</div>
          <div className="text-gray-500">LLM</div>
        </div>
        <div className="text-center bg-white dark:bg-gray-700 rounded p-1">
          <div className="font-bold text-gray-700 dark:text-gray-300">{extractionDiagnostics.attempts?.length || 0}</div>
          <div className="text-gray-500">Logged</div>
        </div>
      </div>
      {extractionDiagnostics.attempts && extractionDiagnostics.attempts.length > 0 && (
        <div className="space-y-0.5 max-h-16 overflow-auto">
          {extractionDiagnostics.attempts.map((attempt: any, idx: number) => (
            <div
              key={idx}
              className={`flex items-center gap-1 text-[8px] px-1.5 py-0.5 rounded ${
                attempt.success ? "bg-green-100 dark:bg-green-900/30" : "bg-red-100 dark:bg-red-900/30"
              }`}
            >
              <span className={`font-bold ${attempt.success ? "text-green-700" : "text-red-700"}`}>{attempt.success ? "✓" : "✗"}</span>
              <span className="font-medium text-indigo-600">{attempt.strategy}</span>
              <span className="text-gray-500">llm:{attempt.llmCalls}</span>
              {attempt.errorCategory && <span className="text-red-600">{attempt.errorCategory}</span>}
              {attempt.issueCodes && <span className="text-orange-600">[{attempt.issueCodes.join(",")}]</span>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

