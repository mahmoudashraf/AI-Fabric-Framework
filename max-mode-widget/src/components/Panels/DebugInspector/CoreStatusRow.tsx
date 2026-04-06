export function CoreStatusRow({
  result,
  metadata,
}: {
  result: any;
  metadata: any;
}) {
  return (
    <div className="grid grid-cols-4 gap-1.5">
      <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2 text-center">
        <span
          className={`inline-block px-1.5 py-0.5 text-[10px] font-bold rounded ${
            result?.success ? "bg-green-500 text-white" : "bg-red-500 text-white"
          }`}
        >
          {result?.success ? "OK" : "ERR"}
        </span>
        <div className="text-[9px] text-gray-500 mt-0.5">Status</div>
      </div>
      <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2 text-center">
        <span
          className={`inline-block px-1.5 py-0.5 text-[10px] font-bold rounded ${
            result?.type === "ACTION_EXECUTED"
              ? "bg-green-500 text-white"
              : result?.type === "CONFIRMATION_REQUIRED"
                ? "bg-yellow-500 text-white"
                : result?.type === "CLARIFICATION_REQUIRED"
                  ? "bg-orange-500 text-white"
                  : result?.type === "COMPOUND_HANDLED"
                    ? "bg-purple-500 text-white"
                    : result?.type === "INFORMATION_PROVIDED"
                      ? "bg-blue-500 text-white"
                      : result?.type === "ACTION_DENIED"
                        ? "bg-red-500 text-white"
                        : "bg-gray-500 text-white"
          }`}
        >
          {result?.type?.replace(/_/g, " ").substring(0, 12) || "N/A"}
        </span>
        <div className="text-[9px] text-gray-500 mt-0.5">Type</div>
      </div>
      <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2 text-center">
        <span className="text-[10px] font-bold text-gray-700 dark:text-gray-300">{result?.errorCode || "—"}</span>
        <div className="text-[9px] text-gray-500 mt-0.5">Error</div>
      </div>
      <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2 text-center">
        <span className="text-[10px] font-bold text-gray-700 dark:text-gray-300 truncate block">
          {metadata?.requestId?.substring(0, 8) || "—"}
        </span>
        <div className="text-[9px] text-gray-500 mt-0.5">ReqID</div>
      </div>
    </div>
  );
}

