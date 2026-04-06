import { motion } from "framer-motion";

import { MaxModeHeader } from "./MaxModeHeader";
import { MaxModeCollectionAnimation } from "./MaxModeView/MaxModeCollectionAnimation";
import { MaxModeComposerBar } from "./MaxModeView/MaxModeComposerBar";
import { MaxModeMainContent } from "./MaxModeView/MaxModeMainContent";
import { MaxModeOverlays } from "./MaxModeView/MaxModeOverlays";
import { MaxModeQuickActions } from "./MaxModeView/MaxModeQuickActions";

import type { MaxModeController } from "@/hooks/useMaxModeController";

export function MaxModeView({
  onClose,
  controller,
}: {
  onClose: () => void;
  controller: MaxModeController;
}) {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-[100] bg-gradient-to-br from-blue-50 via-blue-50/50 to-white dark:from-gray-900 dark:via-blue-900/20 dark:to-gray-900/90"
    >
      <MaxModeHeader onClose={onClose} onShowSampleDocuments={controller.showSampleDocuments} />
      <MaxModeQuickActions controller={controller} />
      <MaxModeMainContent controller={controller} />
      <MaxModeCollectionAnimation collectingItem={controller.collectingItem} />
      <MaxModeComposerBar controller={controller} />
      <MaxModeOverlays controller={controller} />
    </motion.div>
  );
}

