'use client';

import Slider from '@mui/material/Slider';
import React from 'react';

// material-ui

// ==============================|| BASIC SLIDER ||============================== //

export default function BasicSlider() {
  const [value, setValue] = React.useState<number | number[]>(50);
  const handleChange = (_event: Event, newValue: number | number[]) => {
    setValue(newValue);
  };

  return <Slider value={value} onChange={handleChange} aria-labelledby="continuous-slider" />;
}
