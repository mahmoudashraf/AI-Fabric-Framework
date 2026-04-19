import type { MaxModeProps } from "@/types";
import { MaxModeView } from "./MaxModeView";
import { useMaxModeController } from "@/hooks/useMaxModeController";

export const MaxModePage = ({
  isOpen,
  onClose,
  assistantLabel,
  showUtilityPanel,
}: MaxModeProps & { assistantLabel?: string; showUtilityPanel?: boolean }) => {
  const controller = useMaxModeController({ isOpen, assistantLabel, showUtilityPanel });

  if (!isOpen) return null;

  return <MaxModeView onClose={onClose} controller={controller} />;
};

export default MaxModePage;
