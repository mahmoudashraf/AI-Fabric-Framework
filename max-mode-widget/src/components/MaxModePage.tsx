import type { MaxModeProps } from "@/types";
import type { MaxModeWidgetConfig } from "@/config";
import { MaxModeView } from "./MaxModeView";
import { useMaxModeController } from "@/hooks/useMaxModeController";

export const MaxModePage = ({
  isOpen,
  onClose,
  assistantLabel,
  showUtilityPanel,
  widgetConfig,
}: MaxModeProps & { assistantLabel?: string; showUtilityPanel?: boolean; widgetConfig?: MaxModeWidgetConfig }) => {
  const controller = useMaxModeController({ isOpen, assistantLabel, showUtilityPanel, widgetConfig });

  if (!isOpen) return null;

  return <MaxModeView onClose={onClose} controller={controller} />;
};

export default MaxModePage;
