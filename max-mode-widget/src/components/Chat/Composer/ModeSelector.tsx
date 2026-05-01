import { useState, useRef, useEffect } from "react";

import { motion, AnimatePresence } from "framer-motion";
import { Compass, BrainCircuit, Microscope, ShoppingCart, Zap, ChevronUp } from "lucide-react";
import type { MaxModeMode } from "@/constants";

const MODES = [
  {
    key: "navigator" as const,
    label: "Navigator",
    shortLabel: "Nav",
    icon: Compass,
    color: "text-blue-600",
    bg: "bg-blue-500",
    bgLight: "bg-blue-50 dark:bg-blue-900/30",
    border: "border-blue-400",
    description: "Browse & discover",
  },
  {
    key: "thinker_deep" as const,
    label: "Thinker",
    shortLabel: "Think",
    icon: BrainCircuit,
    color: "text-cyan-600",
    bg: "bg-cyan-500",
    bgLight: "bg-cyan-50 dark:bg-cyan-900/30",
    border: "border-cyan-400",
    description: "Evidence-first reasoning",
  },
  {
    key: "navigator_deep" as const,
    label: "Deep",
    shortLabel: "Deep",
    icon: Microscope,
    color: "text-purple-600",
    bg: "bg-purple-500",
    bgLight: "bg-purple-50 dark:bg-purple-900/30",
    border: "border-purple-400",
    description: "In-depth reasoning",
  },
  {
    key: "cart_assistant" as const,
    label: "Assistant",
    shortLabel: "Assist",
    icon: ShoppingCart,
    color: "text-emerald-600",
    bg: "bg-emerald-500",
    bgLight: "bg-emerald-50 dark:bg-emerald-900/30",
    border: "border-emerald-400",
    description: "Guided support",
  },
  {
    key: "executor" as const,
    label: "Resolver",
    shortLabel: "Resolve",
    icon: Zap,
    color: "text-amber-600",
    bg: "bg-amber-500",
    bgLight: "bg-amber-50 dark:bg-amber-900/30",
    border: "border-amber-400",
    description: "Resolve and act",
  },
] as const;

export function ModeSelector({
  currentMode,
  availableModes,
  onModeChange,
}: {
  currentMode: MaxModeMode;
  availableModes: MaxModeMode[];
  onModeChange: (mode: MaxModeMode) => void;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const selectableModes = MODES.filter((mode) => availableModes.includes(mode.key));
  if (selectableModes.length <= 1) {
    return null;
  }

  const active = selectableModes.find((m) => m.key === currentMode) || selectableModes[0];
  const ActiveIcon = active.icon;

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
      return () => document.removeEventListener("mousedown", handleClickOutside);
    }
  }, [isOpen]);

  return (
    <div ref={containerRef} className="relative">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={`flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold shadow-sm transition-all hover:scale-105 border ${active.bg} text-white border-white/20`}
        title={`Mode: ${active.label} — Click to change`}
      >
        <ActiveIcon className="h-3 w-3" />
        <span>{active.shortLabel}</span>
        <ChevronUp
          className={`h-2.5 w-2.5 transition-transform ${isOpen ? "" : "rotate-180"}`}
        />
      </button>

      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: 4, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 4, scale: 0.95 }}
            transition={{ duration: 0.15 }}
            className="absolute bottom-full right-0 mb-1.5 w-44 bg-white dark:bg-gray-800 rounded-xl shadow-xl border border-gray-200 dark:border-gray-700 overflow-hidden"
          >
            <div className="p-1">
              {selectableModes.map((mode) => {
                const Icon = mode.icon;
                const isActive = mode.key === currentMode;
                return (
                  <button
                    key={mode.key}
                    onClick={() => {
                      onModeChange(mode.key);
                      setIsOpen(false);
                    }}
                    className={`w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-left transition-all ${
                      isActive
                        ? `${mode.bgLight} ${mode.border} border`
                        : "hover:bg-gray-50 dark:hover:bg-gray-700/50 border border-transparent"
                    }`}
                  >
                    <div
                      className={`h-6 w-6 rounded-md flex items-center justify-center ${
                        isActive ? `${mode.bg} text-white` : "bg-gray-100 dark:bg-gray-700"
                      }`}
                    >
                      <Icon className="h-3.5 w-3.5" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className={`text-[11px] font-semibold ${isActive ? mode.color : "text-gray-700 dark:text-gray-300"}`}>
                        {mode.label}
                      </div>
                      <div className="text-[9px] text-gray-400 dark:text-gray-500">{mode.description}</div>
                    </div>
                    {isActive && (
                      <div className={`h-1.5 w-1.5 rounded-full ${mode.bg}`} />
                    )}
                  </button>
                );
              })}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
