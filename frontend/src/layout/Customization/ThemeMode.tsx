// material-ui
import { FormControlLabel, PaletteMode, Radio, RadioGroup, Stack, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project-imports
import { IconMoon, IconSun } from '@tabler/icons-react';

import useConfig from 'hooks/useConfig';
import Avatar from 'ui-component/extended/Avatar';

// assets

// ==============================|| CUSTOMIZATION - MODE ||============================== //

const ThemeModeLayout = () => {
  const theme = useTheme();
  const { navType, onChangeMenuType } = useConfig();

  return (
    <Stack
      direction="row"
      alignItems="center"
      p={2}
      justifyContent="space-between"
      spacing={2.5}
      sx={{ width: '100%' }}
    >
      <Typography variant="h5">THEME MODE</Typography>
      <RadioGroup
        row
        aria-label="layout"
        value={navType}
        onChange={e => onChangeMenuType(e.target.value as PaletteMode)}
        name="row-radio-buttons-group"
      >
        <FormControlLabel
          control={<Radio value="light" sx={{ display: 'none' }} />}
          label={
            <Avatar
              variant="rounded"
              outline
              sx={{
                mr: 1,
                width: 48,
                height: 48,
                ...(theme.palette.mode !== 'light' && { borderColor: theme.palette.divider + 20 }),
              }}
            >
              <IconSun color={theme.palette.warning.dark} />
            </Avatar>
          }
        />
        <FormControlLabel
          control={<Radio value="dark" sx={{ display: 'none' }} />}
          label={
            <Avatar
              size="md"
              variant="rounded"
              color="dark"
              sx={{
                width: 48,
                height: 48,
                ...(theme.palette.mode === 'dark' && {
                  border: `2px solid ${theme.palette.primary.main}`,
                }),
              }}
            >
              <IconMoon style={{ transform: 'rotate(220deg)', color: theme.palette.grey[100] }} />
            </Avatar>
          }
        />
      </RadioGroup>
    </Stack>
  );
};

export default ThemeModeLayout;
