import { Sparkles } from "lucide-react";

export function OrchestrationPolicyCard({
  orchestrationPolicy,
}: {
  orchestrationPolicy: any;
}) {
  return (
    <div className="bg-gradient-to-br from-indigo-50 to-purple-50 dark:from-indigo-900/20 dark:to-purple-900/20 rounded-lg p-2 border border-indigo-200 dark:border-indigo-800">
      <h4 className="text-[10px] font-bold text-indigo-700 dark:text-indigo-300 uppercase mb-1 flex items-center gap-1">
        <Sparkles className="h-2.5 w-2.5" /> Orchestration
      </h4>
      <div className="grid grid-cols-3 gap-1 text-[9px]">
        <div className="flex justify-between bg-white/50 dark:bg-gray-800/50 rounded px-1.5 py-1">
          <span className="text-gray-500">Profile</span>
          <span className="font-bold text-indigo-600">{orchestrationPolicy.profile}</span>
        </div>
        <div className="flex justify-between bg-white/50 dark:bg-gray-800/50 rounded px-1.5 py-1">
          <span className="text-gray-500">Mode</span>
          <span className="font-bold text-purple-600">{orchestrationPolicy.mode}</span>
        </div>
        <div className="flex justify-between bg-white/50 dark:bg-gray-800/50 rounded px-1.5 py-1">
          <span className="text-gray-500">Position</span>
          <span className="font-bold text-cyan-600">{orchestrationPolicy.position}</span>
        </div>
        <div className="flex justify-between bg-white/50 dark:bg-gray-800/50 rounded px-1.5 py-1">
          <span className="text-gray-500">InfoMode</span>
          <span className="font-bold text-teal-600">{orchestrationPolicy.informationModeEffective}</span>
        </div>
        <div className="flex justify-between bg-white/50 dark:bg-gray-800/50 rounded px-1.5 py-1">
          <span className="text-gray-500">Source</span>
          <span className="font-bold text-gray-600">{orchestrationPolicy.modeSource}</span>
        </div>
        {orchestrationPolicy.advancedRagOverride && (
          <div className="flex justify-between bg-white/50 dark:bg-gray-800/50 rounded px-1.5 py-1">
            <span className="text-gray-500">RAG Override</span>
            <span className="font-bold text-orange-600">✓</span>
          </div>
        )}
      </div>
    </div>
  );
}

