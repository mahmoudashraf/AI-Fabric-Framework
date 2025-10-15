'use client';

// material-ui

// assets
import FormatAlignCenterIcon from '@mui/icons-material/FormatAlignCenterTwoTone';
import FormatAlignJustifyIcon from '@mui/icons-material/FormatAlignJustifyTwoTone';
import FormatAlignLeftIcon from '@mui/icons-material/FormatAlignLeftTwoTone';
import FormatAlignRightIcon from '@mui/icons-material/FormatAlignRightTwoTone';
import LaptopIcon from '@mui/icons-material/LaptopTwoTone';
import PhoneAndroidIcon from '@mui/icons-material/PhoneAndroidTwoTone';
import TvIcon from '@mui/icons-material/TvTwoTone';
import { Grid, ToggleButton, ToggleButtonGroup  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import React from 'react';

// ============================|| UI TOGGLE BUTTON - NO EMPTY ||============================ //

export default function ToggleButtonNotEmpty() {
  const theme = useTheme();

  const [formats, setFormats] = React.useState(() => ['phone']);
  const handleFormat = (_event: React.SyntheticEvent, newFormats: string[]) => {
    if (newFormats.length) {
      setFormats(newFormats);
    }
  };

  const [alignment, setAlignment] = React.useState('left');
  const handleAlignment = (_event: React.SyntheticEvent, newAlignment: string) => {
    if (newAlignment !== null) {
      setAlignment(newAlignment);
    }
  };

  return (
    <Grid container spacing={2} direction="column" alignItems="center">
      <Grid size={{ xs: 12 }}>
        <div>
          <ToggleButtonGroup
            value={alignment}
            exclusive
            onChange={handleAlignment}
            aria-label="text alignment"
            sx={{
              color: theme.palette.success.dark,
              bgcolor:
                theme.palette.mode === 'dark'
                  ? theme.palette.dark[800]
                  : theme.palette.primary.light,
            }}
          >
            <ToggleButton value="left" aria-label="left aligned">
              <FormatAlignLeftIcon />
            </ToggleButton>
            <ToggleButton value="center" aria-label="centered">
              <FormatAlignCenterIcon />
            </ToggleButton>
            <ToggleButton value="right" aria-label="right aligned">
              <FormatAlignRightIcon />
            </ToggleButton>
            <ToggleButton value="justify" aria-label="justified" disabled>
              <FormatAlignJustifyIcon />
            </ToggleButton>
          </ToggleButtonGroup>
        </div>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <div>
          <ToggleButtonGroup
            value={formats}
            onChange={handleFormat}
            aria-label="device"
            sx={{
              color: theme.palette.warning.dark,
              bgcolor:
                theme.palette.mode === 'dark'
                  ? theme.palette.dark[800]
                  : theme.palette.primary.light,
            }}
          >
            <ToggleButton value="laptop" aria-label="laptop">
              <LaptopIcon />
            </ToggleButton>
            <ToggleButton value="tv" aria-label="tv">
              <TvIcon />
            </ToggleButton>
            <ToggleButton value="phone" aria-label="phone">
              <PhoneAndroidIcon />
            </ToggleButton>
          </ToggleButtonGroup>
        </div>
      </Grid>
    </Grid>
  );
}
