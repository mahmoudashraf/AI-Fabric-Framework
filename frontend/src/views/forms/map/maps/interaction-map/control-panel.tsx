import { Box, InputBase, Switch, Typography } from '@mui/material';
import { styled, alpha } from '@mui/material/styles';
import { memo } from 'react';

// material-ui

// project-import
import { MapControlKeys, MapControlProps } from 'types/map';
import ControlPanelStyled from 'ui-component/third-party/map/ControlPanelStyled';

// types

const RowStyled = styled(Box)(({ theme }) => ({
  display: 'flex',
  alignItems: 'center',
  textTransform: 'capitalize',
  justifyContent: 'space-between',
  '&:not(:last-of-type)': {
    marginBottom: theme.spacing(1),
  },
}));

const camelPattern = /(^|[A-Z])[a-z]*/g;

function formatSettingName(name: string) {
  return name.match(camelPattern)?.join(' ');
}

type Props = {
  settings: MapControlProps;
  onChange: (name: MapControlKeys, value: boolean | number) => void;
};

// ==============================|| CONTROL - INTERATION MAP ||============================== //

function ControlPanel({ settings, onChange }: Props) {
  const renderSetting = (name: MapControlKeys, value: boolean | number) => {
    switch (typeof value) {
      case 'boolean':
        return (
          <RowStyled key={name}>
            <Typography variant="body2">{formatSettingName(name)}</Typography>
            <Switch
              size="small"
              checked={value}
              onChange={event => onChange(name, event.target.checked)}
            />
          </RowStyled>
        );
      case 'number':
        return (
          <RowStyled key={name}>
            <Typography variant="body2">{formatSettingName(name)}</Typography>
            <InputBase
              value={value}
              onChange={event => onChange(name, Number(event.target.value))}
              inputProps={{ type: 'number' }}
              sx={{
                '& input': {
                  py: 0.25,
                  width: 40,
                  fontSize: 14,
                  borderRadius: 1,
                  textAlign: 'center',
                  bgcolor: theme => alpha(theme.palette.grey[500], 0.12),
                },
              }}
            />
          </RowStyled>
        );
      default:
        return null;
    }
  };

  return (
    <ControlPanelStyled>
      {Object.keys(settings).map(name =>
        renderSetting(name as MapControlKeys, settings[name as MapControlKeys])
      )}
    </ControlPanelStyled>
  );
}

export default memo(ControlPanel);
