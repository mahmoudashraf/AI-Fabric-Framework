'use client';

import { Grid,
  FormControl,
  FormControlLabel,
  FormLabel,
  Paper,
  Radio,
  RadioGroup,
} from '@mui/material';
import * as React from 'react';

// material-ui

// project imports
import Item from './GridItem';

// ===============================|| GRID - SPACING ||=============================== //

export default function SpacingGrid() {
  const [spacing, setSpacing] = React.useState<number>(2);

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSpacing(Number(event.target?.value));
  };

  return (
    <Grid sx={{ flexGrow: 1 }} container spacing={2}>
      <Grid size={{ xs: 12 }}>
        <Grid container justifyContent="center" spacing={spacing}>
          {[0, 1, 2].map(value => (
            <Grid key={value} size={2}>
              <Item>{value}</Item>
            </Grid>
          ))}
        </Grid>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Paper sx={{ p: 2 }}>
          <Grid container>
            <Grid>
              <FormControl component="fieldset">
                <FormLabel component="legend">spacing</FormLabel>
                <RadioGroup
                  name="spacing"
                  aria-label="spacing"
                  value={spacing.toString()}
                  onChange={handleChange}
                  row
                >
                  {[0, 0.5, 1, 2, 3, 4, 8, 12].map(value => (
                    <FormControlLabel
                      key={value}
                      value={value.toString()}
                      control={<Radio />}
                      label={value.toString()}
                    />
                  ))}
                </RadioGroup>
              </FormControl>
            </Grid>
          </Grid>
        </Paper>
      </Grid>
    </Grid>
  );
}
