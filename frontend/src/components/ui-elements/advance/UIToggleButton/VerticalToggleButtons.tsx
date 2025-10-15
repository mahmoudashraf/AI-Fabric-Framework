'use client';

// material-ui

// assets
import ViewListIcon from '@mui/icons-material/ViewListTwoTone';
import ViewModuleIcon from '@mui/icons-material/ViewModuleTwoTone';
import ViewQuiltIcon from '@mui/icons-material/ViewQuiltTwoTone';
import { Grid, ToggleButton, ToggleButtonGroup  } from '@mui/material';
import React from 'react';

// ============================|| UI TOGGLE BUTTON - VERTICAL ||============================ //

export default function VerticalToggleButtons() {
  const [view, setView] = React.useState('list');
  const handleChange = (_event: React.SyntheticEvent, nextView: string) => {
    setView(nextView);
  };

  return (
    <Grid container justifyContent="center">
      <ToggleButtonGroup orientation="vertical" value={view} exclusive onChange={handleChange}>
        <ToggleButton value="list" aria-label="list">
          <ViewListIcon />
        </ToggleButton>
        <ToggleButton value="module" aria-label="module">
          <ViewModuleIcon />
        </ToggleButton>
        <ToggleButton value="quilt" aria-label="quilt">
          <ViewQuiltIcon />
        </ToggleButton>
      </ToggleButtonGroup>
    </Grid>
  );
}
