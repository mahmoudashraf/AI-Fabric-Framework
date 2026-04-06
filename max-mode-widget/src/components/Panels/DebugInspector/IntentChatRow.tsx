export function IntentChatRow({
  intentMetadata,
  chat,
}: {
  intentMetadata: any;
  chat: any;
}) {
  return (
    <div className="grid grid-cols-2 gap-1.5">
      {intentMetadata && (
        <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
          <h4 className="text-[10px] font-bold text-gray-600 uppercase mb-1">Intent</h4>
          <div className="text-[9px] space-y-0.5">
            {intentMetadata.fallback && (
              <div className="flex justify-between text-amber-600">
                <span>Fallback:</span>
                <span className="font-bold">✓</span>
              </div>
            )}
            {intentMetadata.confidence && (
              <div className="flex justify-between">
                <span className="text-gray-500">Confidence:</span>
                <span className="font-medium">{(intentMetadata.confidence * 100).toFixed(0)}%</span>
              </div>
            )}
          </div>
        </div>
      )}
      {chat && (
        <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
          <h4 className="text-[10px] font-bold text-gray-600 uppercase mb-1">Chat</h4>
          <div className="grid grid-cols-2 gap-0.5 text-[9px]">
            <div className="flex justify-between">
              <span className="text-gray-500">History:</span>
              <span className="font-medium">{chat.historyChars || 0}ch</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Window:</span>
              <span className="font-medium">{chat.windowSize || 0}</span>
            </div>
            <div className="flex justify-between col-span-2">
              <span className="text-gray-500">Memory:</span>
              <span className="font-medium">{chat.memoryStrategy || "—"}</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

