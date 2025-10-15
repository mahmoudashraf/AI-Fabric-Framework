'use client';
import CheckIcon from '@mui/icons-material/CheckTwoTone';
import { Grid, ToggleButton  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import React from 'react';

// material-ui

// assets

// ============================|| UI TOGGLE BUTTON - STANDALONE ||============================ //

export default function StandaloneToggleButton() {
  const theme = useTheme();
  const [selected, setSelected] = React.useState(false);

  return (
    <Grid container justifyContent="center">
      <ToggleButton
        value="check"
        aria-label="standalone button"
        onChange={() => {
          setSelected(!selected);
        }}
        sx={{ color: theme.palette.success.dark, bgcolor: theme.palette.success.light }}
      >
        <CheckIcon />
      </ToggleButton>
    </Grid>
  );
}
