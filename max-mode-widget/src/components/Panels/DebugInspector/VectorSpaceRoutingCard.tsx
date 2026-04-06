export function VectorSpaceRoutingCard({ vectorSpaceRouting }: { vectorSpaceRouting: any[] }) {
  return (
    <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
      <h4 className="text-[10px] font-bold text-gray-600 uppercase mb-1">Vector Space Routing ({vectorSpaceRouting.length})</h4>
      <div className="space-y-0.5 max-h-20 overflow-auto">
        {vectorSpaceRouting.map((evt: any, idx: number) => (
          <div key={idx} className="flex items-center gap-1 text-[8px] bg-white dark:bg-gray-700 rounded px-1.5 py-0.5">
            <span className="text-purple-600 font-medium">{evt.strategy}</span>
            <span className="text-gray-400">→</span>
            <span className="text-blue-600 font-bold">{evt.vectorSpace}</span>
            {evt.candidateSpaces && <span className="text-gray-500">[{evt.candidateSpaces.join(",")}]</span>}
          </div>
        ))}
      </div>
    </div>
  );
}

