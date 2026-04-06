import { AnimatePresence, motion } from "framer-motion";
import { BrainCircuit, CheckCircle2, Sparkles } from "lucide-react";

import { Badge } from "@/ui/badge";
import { Card, CardContent } from "@/ui/card";

import type { MaxModeController } from "@/hooks/useMaxModeController";

export function MaxModeCollectionAnimation({ collectingItem }: { collectingItem: MaxModeController["collectingItem"] }) {
  return (
    <AnimatePresence>
      {collectingItem && (
        <motion.div
          initial={{ y: 200, opacity: 0, scale: 0.8 }}
          animate={{ y: 0, opacity: 1, scale: 1 }}
          exit={{ y: 200, opacity: 0, scale: 0.8 }}
          transition={{ type: "spring", damping: 15, stiffness: 200 }}
          className="fixed bottom-28 md:bottom-32 left-0 right-0 z-[110] pointer-events-none px-4 flex justify-center"
        >
          <motion.div
            animate={{
              rotate: [0, -2, 2, -2, 0],
              scale: [1, 1.05, 1, 1.05, 1],
            }}
            transition={{ duration: 0.5, repeat: 2 }}
            className="relative w-full max-w-[350px]"
          >
            <Card className="border-4 border-blue-400 bg-gradient-to-br from-blue-50 to-white shadow-2xl w-full">
              <CardContent className="p-4">
                <div className="flex items-center gap-3">
                  <motion.div
                    initial={{ scale: 0 }}
                    animate={{ scale: [0, 1.3, 1] }}
                    transition={{ delay: 0.2, type: "spring", damping: 10 }}
                    className="relative"
                  >
                    <div className="p-3 bg-gradient-to-br from-purple-500 to-pink-500 rounded-xl shadow-lg">
                      <BrainCircuit className="h-6 w-6 text-white" />
                    </div>
                    <motion.div
                      initial={{ scale: 0, rotate: -180 }}
                      animate={{ scale: 1, rotate: 0 }}
                      transition={{ delay: 0.4, type: "spring", damping: 10 }}
                      className="absolute -top-1 -right-1 bg-white rounded-full p-0.5 shadow-lg"
                    >
                      <CheckCircle2 className="h-4 w-4 text-purple-600" />
                    </motion.div>
                  </motion.div>

                  <div className="flex-1 min-w-0">
                    <motion.p
                      initial={{ opacity: 0, x: -10 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: 0.3 }}
                      className="text-xs font-bold text-purple-800 mb-1 flex items-center gap-1"
                    >
                      <Sparkles className="h-3 w-3" />
                      Added to Context!
                    </motion.p>
                    <motion.p
                      initial={{ opacity: 0, x: -10 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: 0.4 }}
                      className="text-sm font-semibold text-gray-900 line-clamp-2 leading-tight"
                    >
                      {collectingItem.title}
                    </motion.p>
                    <motion.p
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      transition={{ delay: 0.5 }}
                      className="text-[10px] text-purple-700 mt-1 flex items-center gap-1"
                    >
                      <Badge
                        variant="outline"
                        className="text-[9px] bg-purple-100 border-purple-300 text-purple-800 px-1.5 py-0"
                      >
                        {collectingItem.type}
                      </Badge>
                      AI will use this in chat
                    </motion.p>
                  </div>
                </div>
              </CardContent>
            </Card>

            <motion.div
              initial={{ scale: 0, opacity: 0 }}
              animate={{ scale: [0, 1.5, 0], opacity: [0, 1, 0] }}
              transition={{ duration: 0.8, delay: 0.2 }}
              className="absolute inset-0 flex items-center justify-center"
            >
              <div className="text-yellow-400">
                <Sparkles className="h-12 w-12" />
              </div>
            </motion.div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

