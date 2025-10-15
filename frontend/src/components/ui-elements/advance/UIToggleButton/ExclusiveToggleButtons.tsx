'use client';

// material-ui

// assets
import FormatAlignCenterIcon from '@mui/icons-material/FormatAlignCenterTwoTone';
import FormatAlignJustifyIcon from '@mui/icons-material/FormatAlignJustifyTwoTone';
import FormatAlignLeftIcon from '@mui/icons-material/FormatAlignLeftTwoTone';
import FormatAlignRightIcon from '@mui/icons-material/FormatAlignRightTwoTone';
import { Grid, ToggleButton, ToggleButtonGroup  } from '@mui/material';
import React from 'react';

// ============================|| UI TOGGLE BUTTON - EXCLUSIVE ||============================ //

export default function ExclusiveToggleButtons() {
  const [alignment, setAlignment] = React.useState('left');
  const handleAlignment = (_event: React.SyntheticEvent, newAlignment: string) => {
    setAlignment(newAlignment);
  };

  return (
    <Grid container justifyContent="center">
      <ToggleButtonGroup
        value={alignment}
        exclusive
        onChange={handleAlignment}
        aria-label="text alignment"
        color="primary"
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
    </Grid>
  );
}
