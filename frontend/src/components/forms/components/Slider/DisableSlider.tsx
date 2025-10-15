'use client';

import Slider from '@mui/material/Slider';
import React from 'react';

// material-ui

// ==============================|| DISABLED SLIDER ||============================== //

export default function DisableSlider() {
  const [value, setValue] = React.useState<number | number[]>(35);

  const handleChange = (_event: Event, newValue: number | number[]) => {
    setValue(newValue);
  };

  return (
    <Slider disabled value={value} onChange={handleChange} aria-labelledby="continuous-slider" />
  );
}
