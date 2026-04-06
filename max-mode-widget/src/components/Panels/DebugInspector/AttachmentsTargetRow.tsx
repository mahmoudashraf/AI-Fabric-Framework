export function AttachmentsTargetRow({
  attachments,
  attachmentsPrompt,
  targetResolution,
}: {
  attachments: any;
  attachmentsPrompt: any;
  targetResolution: any;
}) {
  return (
    <div className="grid grid-cols-2 gap-1.5">
      {attachments && (
        <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
          <h4 className="text-[10px] font-bold text-gray-600 uppercase mb-1">Attachments</h4>
          <div className="grid grid-cols-2 gap-0.5 text-[9px]">
            <div className="flex justify-between">
              <span className="text-gray-500">Prov:</span>
              <span className="font-medium">{attachments.providedCount}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Acc:</span>
              <span className="font-medium">{attachments.acceptedCount}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Active:</span>
              <span className="font-medium">{attachments.activeResolvedCount}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Invalid:</span>
              <span className="font-medium">{attachments.invalidVectorSpacesCount || 0}</span>
            </div>
          </div>
          {attachmentsPrompt && (
            <div className="mt-1 pt-1 border-t border-gray-200 dark:border-gray-700 text-[9px]">
              <span className="text-gray-500">Prompt: </span>
              <span className={`font-medium ${attachmentsPrompt.injected ? "text-green-600" : "text-gray-400"}`}>
                {attachmentsPrompt.injected ? `✓ ${attachmentsPrompt.attachmentsCount} att` : "—"}
              </span>
            </div>
          )}
        </div>
      )}
      {targetResolution && (
        <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
          <h4 className="text-[10px] font-bold text-gray-600 uppercase mb-1">Target Resolution</h4>
          <div className="text-[9px] space-y-0.5">
            <div className="flex justify-between">
              <span className="text-gray-500">Source:</span>
              <span className="font-medium">{targetResolution.source}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Count:</span>
              <span className="font-medium">{targetResolution.count}</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

