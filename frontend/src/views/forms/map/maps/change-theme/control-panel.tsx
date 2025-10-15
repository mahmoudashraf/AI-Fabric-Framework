import { FormControlLabel, Radio, RadioGroup, Typography } from '@mui/material';
import { memo } from 'react';

// material-ui

// project-import
import ControlPanelStyled from 'ui-component/third-party/map/ControlPanelStyled';

// ==============================|| MAPBOX - THEME ||============================== //

type Props = {
  themes: {
    [key: string]: string;
  };
  selectTheme: string;
  onChangeTheme: (theme: string) => void;
};

function ControlPanel({ themes, selectTheme, onChangeTheme }: Props) {
  return (
    <ControlPanelStyled>
      <Typography gutterBottom variant="subtitle2">
        Select variants:
      </Typography>

      <RadioGroup value={selectTheme} onChange={(_event, newValue) => onChangeTheme(newValue)}>
        {Object.keys(themes).map(item => (
          <FormControlLabel
            key={item}
            value={item}
            control={<Radio size="small" />}
            label={item}
            sx={{ textTransform: 'capitalize' }}
          />
        ))}
      </RadioGroup>
    </ControlPanelStyled>
  );
}

export default memo(ControlPanel);
