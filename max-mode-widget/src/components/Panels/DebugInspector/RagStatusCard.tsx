import { ChevronDown, ChevronUp } from "lucide-react";

export function RagStatusCard({
  debugRequest,
  resultData,
  metadata,
  ragExecuted,
  requiresRetrieval,
  retrievalSkipped,
  retrievalSkipReason,
  hasRagResponse,
  isQueryExpanded,
  setIsQueryExpanded,
}: {
  debugRequest: any;
  resultData: any;
  metadata: any;
  ragExecuted: boolean;
  requiresRetrieval: boolean;
  retrievalSkipped: boolean;
  retrievalSkipReason: any;
  hasRagResponse: boolean;
  isQueryExpanded: boolean;
  setIsQueryExpanded: (expanded: boolean) => void;
}) {
  return (
    <div
      className={`rounded-lg p-2 border ${
        ragExecuted
          ? "bg-gradient-to-r from-emerald-50 to-teal-50 dark:from-emerald-900/20 dark:to-teal-900/20 border-emerald-300 dark:border-emerald-700"
          : retrievalSkipped
            ? "bg-gradient-to-r from-amber-50 to-orange-50 dark:from-amber-900/20 dark:to-orange-900/20 border-amber-300 dark:border-amber-700"
            : requiresRetrieval
              ? "bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-900/20 dark:to-indigo-900/20 border-blue-300 dark:border-blue-700"
              : "bg-gray-50 dark:bg-gray-800 border-gray-200 dark:border-gray-700"
      }`}
    >
      <div className="space-y-1">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span
              className={`text-[10px] font-bold uppercase ${
                ragExecuted
                  ? "text-emerald-700 dark:text-emerald-300"
                  : retrievalSkipped
                    ? "text-amber-700 dark:text-amber-300"
                    : "text-gray-600 dark:text-gray-400"
              }`}
            >
              🔍 RAG Status
            </span>
            <span
              className={`px-1.5 py-0.5 text-[9px] font-bold rounded ${
                ragExecuted
                  ? "bg-emerald-500 text-white"
                  : retrievalSkipped
                    ? "bg-amber-500 text-white"
                    : requiresRetrieval
                      ? "bg-blue-500 text-white"
                      : "bg-gray-400 text-white"
              }`}
            >
              {ragExecuted ? "EXECUTED" : retrievalSkipped ? "SKIPPED" : requiresRetrieval ? "INTENDED" : "NOT REQUIRED"}
            </span>
          </div>
          <div className="flex gap-2 text-[9px]">
            {requiresRetrieval && <span className="text-blue-600">retrieval:✓</span>}
            {resultData?.requiresGeneration && <span className="text-purple-600">generation:✓</span>}
            {retrievalSkipped && <span className="text-amber-600">skip:{retrievalSkipReason}</span>}
          </div>
        </div>
        {hasRagResponse && (
          <div className="flex items-start gap-1 text-[9px]">
            <span className="text-gray-500 font-medium shrink-0">Query:</span>
            <span className="text-gray-700 dark:text-gray-300 line-clamp-1 flex-1">
              {debugRequest?.payload?.query || resultData.ragResponse.query}
            </span>
          </div>
        )}
      </div>

      {hasRagResponse &&
        (() => {
          const originalQuery = debugRequest?.payload?.query || resultData.ragResponse.query;
          const optimizedQuery = resultData.ragResponse.optimizedQuery || resultData.ragResponse.query;
          const embeddingQuery = resultData?.metadata?.embeddingQuery || resultData?.ragResponse?.metadata?.embeddingQuery || metadata?.embeddingQuery;
          const charLimit = 100;
          const shouldShowExpand = originalQuery?.length > charLimit || optimizedQuery?.length > charLimit || (embeddingQuery?.length ?? 0) > charLimit;

          return (
            <div className="mt-1.5 space-y-1.5">
              <div className="bg-white/50 dark:bg-gray-700/50 rounded-lg p-2 space-y-1.5">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-bold text-gray-600 dark:text-gray-400 uppercase">Queries</span>
                  {shouldShowExpand && (
                    <button
                      onClick={() => setIsQueryExpanded(!isQueryExpanded)}
                      className="flex items-center gap-1 text-[9px] text-blue-600 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300 transition-colors"
                    >
                      {isQueryExpanded ? (
                        <>
                          <ChevronUp className="h-3 w-3" />
                          <span>See less</span>
                        </>
                      ) : (
                        <>
                          <ChevronDown className="h-3 w-3" />
                          <span>See more</span>
                        </>
                      )}
                    </button>
                  )}
                </div>

                <div className="space-y-0.5">
                  <div className="flex items-center gap-1">
                    <span className="text-[9px] text-gray-500 font-medium">Original:</span>
                    <span className="px-1 py-0.5 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 rounded text-[8px] font-bold">
                      USER INPUT
                    </span>
                  </div>
                  <p
                    className={`text-[10px] text-gray-800 dark:text-gray-200 ${
                      !isQueryExpanded && originalQuery?.length > charLimit ? "line-clamp-2" : ""
                    }`}
                  >
                    {isQueryExpanded
                      ? originalQuery
                      : originalQuery?.length > charLimit
                        ? `${originalQuery.substring(0, charLimit)}...`
                        : originalQuery}
                  </p>
                </div>

                {optimizedQuery && optimizedQuery !== originalQuery && (
                  <div className="space-y-0.5 pt-1 border-t border-gray-200 dark:border-gray-600">
                    <div className="flex items-center gap-1">
                      <span className="text-[9px] text-gray-500 font-medium">Optimized:</span>
                      <span className="px-1 py-0.5 bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300 rounded text-[8px] font-bold">
                        FOR EMBEDDINGS
                      </span>
                    </div>
                    <p
                      className={`text-[10px] text-gray-800 dark:text-gray-200 ${
                        !isQueryExpanded && optimizedQuery?.length > charLimit ? "line-clamp-2" : ""
                      }`}
                    >
                      {isQueryExpanded
                        ? optimizedQuery
                        : optimizedQuery?.length > charLimit
                          ? `${optimizedQuery.substring(0, charLimit)}...`
                          : optimizedQuery}
                    </p>
                  </div>
                )}

                {embeddingQuery && (
                  <div className="space-y-0.5 pt-1 border-t border-gray-200 dark:border-gray-600">
                    <div className="flex items-center gap-1">
                      <span className="text-[9px] text-gray-500 font-medium">Embedding:</span>
                      <span className="px-1 py-0.5 bg-cyan-100 dark:bg-cyan-900/30 text-cyan-700 dark:text-cyan-300 rounded text-[8px] font-bold">
                        VECTOR SEARCH
                      </span>
                    </div>
                    <p
                      className={`text-[10px] text-gray-800 dark:text-gray-200 ${
                        !isQueryExpanded && embeddingQuery?.length > charLimit ? "line-clamp-2" : ""
                      }`}
                    >
                      {isQueryExpanded
                        ? embeddingQuery
                        : embeddingQuery?.length > charLimit
                          ? `${embeddingQuery.substring(0, charLimit)}...`
                          : embeddingQuery}
                    </p>
                  </div>
                )}
              </div>

              <div className="grid grid-cols-3 gap-1 text-[9px]">
                <div className="text-center bg-white/50 dark:bg-gray-700/50 rounded px-1.5 py-1">
                  <div className="text-gray-500 text-[8px]">Entity</div>
                  <div className="font-bold text-gray-700 dark:text-gray-300">{resultData.ragResponse.entityType}</div>
                </div>
                <div className="text-center bg-white/50 dark:bg-gray-700/50 rounded px-1.5 py-1">
                  <div className="text-gray-500 text-[8px]">Docs</div>
                  <div className="font-bold text-gray-700 dark:text-gray-300">
                    {resultData.ragResponse.usedDocuments || resultData.documents?.length || 0}
                  </div>
                </div>
                <div className="text-center bg-white/50 dark:bg-gray-700/50 rounded px-1.5 py-1">
                  <div className="text-gray-500 text-[8px]">Time</div>
                  <div className="font-bold text-gray-700 dark:text-gray-300">{resultData.ragResponse.processingTimeMs}ms</div>
                </div>
              </div>
            </div>
          );
        })()}
    </div>
  );
}

