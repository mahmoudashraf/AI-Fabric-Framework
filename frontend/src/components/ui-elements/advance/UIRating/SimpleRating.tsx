'use client';

import { Grid, Rating, Typography  } from '@mui/material';
import React from 'react';

// material-ui

// ===============================|| UI RATING - SIMPLE ||=============================== //

export default function SimpleRating() {
  const [value, setValue] = React.useState<number | null>(2);

  return (
    <>
      <Grid container spacing={2} justifyContent="center">
        <Grid>
          <Typography component="legend">Controlled</Typography>
        </Grid>
        <Grid>
          <Rating
            name="simple-controlled"
            value={value}
            onChange={(_event: React.SyntheticEvent<Element, Event>, newValue: number | null) => {
              setValue(newValue);
            }}
          />
        </Grid>
      </Grid>
      <Grid container spacing={2} justifyContent="center">
        <Grid>
          <Typography component="legend">Read only</Typography>
        </Grid>
        <Grid>
          <Rating name="read-only" value={value} readOnly />
        </Grid>
      </Grid>
      <Grid container spacing={2} justifyContent="center">
        <Grid>
          <Typography component="legend">Disabled</Typography>
        </Grid>
        <Grid>
          <Rating name="disabled" value={value} disabled />
        </Grid>
      </Grid>
      <Grid container spacing={2} justifyContent="center">
        <Grid>
          <Typography component="legend">Pristine</Typography>
        </Grid>
        <Grid>
          <Rating name="pristine" value={null} />
        </Grid>
      </Grid>
    </>
  );
}
