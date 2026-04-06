import { motion } from "framer-motion";
import { Search, X } from "lucide-react";

interface AISearchDisplayProps {
  category: string;
  onRemove: () => void;
}

export const AISearchDisplay = ({ category, onRemove }: AISearchDisplayProps) => {
  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      className="absolute top-2 left-2 z-10"
    >
      <div className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-gradient-to-r from-cyan-500 via-blue-500 to-purple-600 rounded-lg shadow-lg border border-white/30">
        <div className="flex items-center justify-center h-4 w-4 rounded-md bg-white/25">
          <Search className="h-2.5 w-2.5 text-white" />
        </div>
        <div className="flex items-center gap-1.5">
          <span className="text-[8px] text-white/90 font-semibold uppercase tracking-wide">
            AI
          </span>
          <div className="h-3 w-px bg-white/40" />
          <span className="text-[10px] text-white font-bold max-w-[120px] truncate">
            {category}
          </span>
        </div>
        <button
          onClick={onRemove}
          className="h-4 w-4 rounded-md bg-white/20 hover:bg-white/35 flex items-center justify-center transition-all hover:scale-105 ml-0.5"
          title="Clear AI Search"
        >
          <X className="h-2.5 w-2.5 text-white stroke-[3]" />
        </button>
      </div>
    </motion.div>
  );
};
