import { ChevronDown, ChevronUp, Lightbulb } from "lucide-react";
import { useState } from "react";

export function SmartSuggestionCard({
  smartSuggestion,
  nextSteps,
}: {
  smartSuggestion: any;
  nextSteps: any[] | undefined;
}) {
  const [isExpanded, setIsExpanded] = useState(false);

  return (
    <div className="rounded-lg p-2 border bg-gradient-to-r from-amber-50 to-yellow-50 dark:from-amber-900/20 dark:to-yellow-900/20 border-amber-300 dark:border-amber-700">
      <div className="space-y-1">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-bold uppercase text-amber-700 dark:text-amber-300">
              💡 Smart Suggestion
            </span>
            <span className="px-1.5 py-0.5 text-[9px] font-bold rounded bg-amber-500 text-white">
              {smartSuggestion.priority || "PRIMARY"}
            </span>
          </div>
          <span className="text-[9px] font-bold text-amber-600 dark:text-amber-400">
            {Math.round((smartSuggestion.confidence ?? 0) * 100)}%
          </span>
        </div>

        <div className="flex items-start gap-1 text-[9px]">
          <span className="text-gray-500 font-medium shrink-0">Intent:</span>
          <span className="text-gray-700 dark:text-gray-300">{smartSuggestion.intent}</span>
        </div>

        <div className="flex items-start gap-1 text-[9px]">
          <span className="text-gray-500 font-medium shrink-0">Title:</span>
          <span className="text-gray-700 dark:text-gray-300 font-semibold">{smartSuggestion.title}</span>
        </div>

        <div className="flex items-start gap-1 text-[9px]">
          <span className="text-gray-500 font-medium shrink-0">Query:</span>
          <span className="text-gray-700 dark:text-gray-300">{smartSuggestion.query}</span>
        </div>

        {smartSuggestion.rationale && (
          <div className="flex items-start gap-1 text-[9px]">
            <span className="text-gray-500 font-medium shrink-0">Rationale:</span>
            <span className="text-gray-600 dark:text-gray-400 italic">{smartSuggestion.rationale}</span>
          </div>
        )}

        {smartSuggestion.documents && smartSuggestion.documents.length > 0 && (
          <div className="mt-1">
            <button
              onClick={() => setIsExpanded(!isExpanded)}
              className="flex items-center gap-1 text-[9px] text-amber-600 hover:text-amber-700 dark:text-amber-400 dark:hover:text-amber-300 transition-colors"
            >
              {isExpanded ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
              <span>{smartSuggestion.documents.length} suggestion docs</span>
            </button>
            {isExpanded && (
              <div className="mt-1 space-y-0.5 max-h-24 overflow-auto">
                {smartSuggestion.documents.map((doc: any, idx: number) => (
                  <div key={idx} className="text-[8px] bg-white/50 dark:bg-gray-700/50 rounded px-1.5 py-1">
                    <div className="flex items-center gap-1">
                      <span className="px-1 py-0.5 bg-amber-100 dark:bg-amber-900/40 text-amber-600 dark:text-amber-300 rounded text-[7px] font-bold">
                        {doc.type || "doc"}
                      </span>
                      <span className="text-gray-500">id={doc.id}</span>
                      {doc.score != null && (
                        <span className="text-amber-600 dark:text-amber-400 font-bold ml-auto">
                          {doc.score.toFixed(3)}
                        </span>
                      )}
                    </div>
                    {doc.content && (
                      <p className="text-gray-600 dark:text-gray-400 line-clamp-1 mt-0.5">
                        {doc.content}
                      </p>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {smartSuggestion.metadata && (
          <div className="grid grid-cols-2 gap-1 mt-1.5 text-[8px]">
            {smartSuggestion.metadata.vectorSpace && (
              <div className="bg-white/50 dark:bg-gray-700/50 rounded px-1.5 py-0.5">
                <span className="text-gray-400">vectorSpace:</span>{" "}
                <span className="text-gray-700 dark:text-gray-300 font-semibold">{smartSuggestion.metadata.vectorSpace}</span>
              </div>
            )}
            {smartSuggestion.metadata.source && (
              <div className="bg-white/50 dark:bg-gray-700/50 rounded px-1.5 py-0.5">
                <span className="text-gray-400">source:</span>{" "}
                <span className="text-gray-700 dark:text-gray-300 font-semibold">{smartSuggestion.metadata.source}</span>
              </div>
            )}
          </div>
        )}
      </div>

      {nextSteps && nextSteps.length > 0 && (
        <div className="mt-2 pt-2 border-t border-amber-200 dark:border-amber-700/50">
          <div className="flex items-center gap-1.5 mb-1">
            <Lightbulb className="h-2.5 w-2.5 text-blue-500" />
            <span className="text-[9px] font-bold text-blue-600 dark:text-blue-400 uppercase">
              Next Steps ({nextSteps.length})
            </span>
          </div>
          <div className="space-y-0.5">
            {nextSteps.map((step: any, idx: number) => (
              <div key={idx} className="text-[9px] bg-blue-50/50 dark:bg-blue-900/20 rounded px-1.5 py-1 border border-blue-200/50 dark:border-blue-700/50">
                <div className="flex items-center gap-1">
                  <span className="px-1 py-0.5 bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-300 rounded text-[7px] font-bold">
                    {step.intent}
                  </span>
                  {step.vectorSpace && (
                    <span className="px-1 py-0.5 bg-purple-100 dark:bg-purple-900/40 text-purple-600 dark:text-purple-300 rounded text-[7px] font-bold">
                      {step.vectorSpace}
                    </span>
                  )}
                  <span className="ml-auto text-[8px] font-bold text-blue-500">
                    {Math.round((step.confidence ?? 0) * 100)}%
                  </span>
                </div>
                <p className="text-gray-700 dark:text-gray-300 mt-0.5 font-medium">{step.query}</p>
                {step.rationale && (
                  <p className="text-gray-400 dark:text-gray-500 italic">{step.rationale}</p>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
