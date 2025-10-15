'use client';

import { Grid, Slider, Typography  } from '@mui/material';
import React from 'react';

// ==============================|| LABEL SLIDER ||============================== //

export default function LabelSlider() {
  const [value, setValue] = React.useState<number | number[]>(50);
  const handleChange = (_even: Event, newValue: number | number[]) => {
    setValue(newValue);
  };

  const [valueSecondary, setValueSecondary] = React.useState<number | number[]>(78);
  const handleChangeSecondary = (_event: Event, newValue: number | number[]) => {
    setValueSecondary(newValue);
  };

  return (
    <Grid container direction="column">
      <Grid size={{ xs: 12 }} container spacing={2} alignItems="center" justifyContent="center">
        <Grid>
          <Typography variant="caption">Progress</Typography>
        </Grid>
        <Grid size="grow">
          <Slider value={value} onChange={handleChange} aria-labelledby="continuous-slider" />
        </Grid>
        <Grid>
          <Typography variant="h6">{value}%</Typography>
        </Grid>
      </Grid>
      <Grid size={{ xs: 12 }} container spacing={2} alignItems="center" justifyContent="center">
        <Grid>
          <Typography variant="caption">Progress</Typography>
        </Grid>
        <Grid size="grow">
          <Slider
            value={valueSecondary}
            color="secondary"
            onChange={handleChangeSecondary}
            aria-labelledby="continuous-slider"
          />
        </Grid>
        <Grid>
          <Typography variant="h6">{valueSecondary}%</Typography>
        </Grid>
      </Grid>
    </Grid>
  );
}
