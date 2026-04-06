import type { MaxModeProps } from "@/types";
import { MaxModeView } from "./MaxModeView";
import { useMaxModeController } from "@/hooks/useMaxModeController";

export const MaxModePage = ({ isOpen, onClose }: MaxModeProps) => {
  const controller = useMaxModeController({ isOpen });

  if (!isOpen) return null;

  return <MaxModeView onClose={onClose} controller={controller} />;
};

export default MaxModePage;
