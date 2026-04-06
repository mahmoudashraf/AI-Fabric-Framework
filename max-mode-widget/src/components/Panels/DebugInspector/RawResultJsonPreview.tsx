import { Maximize2 } from "lucide-react";

export function RawResultJsonPreview({ result, onExpandJson }: { result: any; onExpandJson: () => void }) {
  return (
    <div className="bg-gray-50 dark:bg-gray-800 rounded-lg overflow-hidden">
      <div className="flex items-center justify-between px-2 py-1.5 border-b border-gray-200 dark:border-gray-700">
        <span className="text-[10px] font-bold text-gray-500 uppercase">Raw Result JSON</span>
        <button onClick={onExpandJson} className="flex items-center gap-1 text-[9px] text-purple-600 hover:text-purple-700 font-medium">
          <Maximize2 className="h-3 w-3" />
          Expand
        </button>
      </div>
      <pre className="px-2 py-1.5 text-[8px] overflow-auto max-h-24 text-gray-600 dark:text-gray-400 font-mono">
        {JSON.stringify(result, null, 2)}
      </pre>
    </div>
  );
}

