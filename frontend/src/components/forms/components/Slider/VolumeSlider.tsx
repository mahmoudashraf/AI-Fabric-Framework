'use client';

// material-ui

// assets
import VolumeDown from '@mui/icons-material/VolumeDown';
import VolumeUp from '@mui/icons-material/VolumeUp';
import { Grid, Slider  } from '@mui/material';
import React from 'react';

// ==============================|| VOLUME SLIDER ||============================== //

export default function VolumeSlider() {
  const [value, setValue] = React.useState<number | number[]>(30);
  const handleChange = (_event: Event, newValue: number | number[]) => {
    setValue(newValue);
  };

  return (
    <Grid container spacing={2} alignItems="center">
      <Grid>
        <VolumeDown color="primary" />
      </Grid>
      <Grid size="grow">
        <Slider value={value} onChange={handleChange} aria-labelledby="continuous-slider" />
      </Grid>
      <Grid>
        <VolumeUp color="primary" />
      </Grid>
    </Grid>
  );
}
