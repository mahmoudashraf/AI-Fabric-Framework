import { ToggleButton, ToggleButtonGroup } from '@mui/material';
import { MouseEvent, memo } from 'react';

// material-ui

// project-import
import ControlPanelStyled from 'ui-component/third-party/map/ControlPanelStyled';

export type ModeProps = 'side-by-side' | 'split-screen';

type Props = {
  mode: ModeProps;
  onModeChange: (event: MouseEvent<HTMLElement>, newMode: ModeProps | null) => void;
};

// ==============================|| SIDE BY SIDE - CONTROL ||============================== //

const ControlPanel = ({ mode, onModeChange }: Props) => {
  return (
    <ControlPanelStyled>
      <ToggleButtonGroup value={mode} exclusive onChange={onModeChange}>
        <ToggleButton value="side-by-side">Side by side</ToggleButton>
        <ToggleButton value="split-screen">Split screen</ToggleButton>
      </ToggleButtonGroup>
    </ControlPanelStyled>
  );
};

export default memo(ControlPanel);
