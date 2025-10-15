'use client';

import FavoriteIcon from '@mui/icons-material/FavoriteTwoTone';
import FileCopyIcon from '@mui/icons-material/FileCopyTwoTone';
import PrintIcon from '@mui/icons-material/PrintTwoTone';
import ShareIcon from '@mui/icons-material/ShareTwoTone';
import {
  Box,
  FormControlLabel,
  FormLabel,
  Radio,
  RadioGroup,
  SpeedDial,
  SpeedDialAction,
  Switch,
} from '@mui/material';
import SpeedDialIcon from '@mui/material/SpeedDialIcon';
import { useTheme } from '@mui/material/styles';
import React from 'react';

// material-ui

// assets

// =============================|| UI SPEEDDIAL - SIMPLE ||============================= //

type Direction = 'up' | 'left' | 'right' | 'down';

export default function SimpleSpeedDials() {
  const theme = useTheme();
  const [open, setOpen] = React.useState(false);

  // fab action options
  const actions = [
    { icon: <FileCopyIcon sx={{ color: theme.palette.grey[700] }} />, name: 'Save' },
    { icon: <PrintIcon sx={{ color: theme.palette.grey[700] }} />, name: 'Print' },
    { icon: <ShareIcon sx={{ color: theme.palette.grey[700] }} />, name: 'Share' },
    { icon: <FavoriteIcon sx={{ color: theme.palette.grey[700] }} />, name: 'Like' },
  ];

  const handleClose = () => {
    setOpen(false);
  };

  const handleOpen = () => {
    setOpen(true);
  };

  const [direction, setDirection] = React.useState<Direction>('up');
  const handleDirectionChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setDirection((event.target.value as Direction) || 'up');
  };

  const [hidden, setHidden] = React.useState(false);
  const handleHiddenChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setHidden(event.target.checked);
  };

  return (
    <div>
      <FormControlLabel
        control={<Switch checked={hidden} onChange={handleHiddenChange} color="primary" />}
        label="Hidden"
      />
      <FormLabel component="legend">Direction</FormLabel>
      <RadioGroup
        sx={{ mt: 1 }}
        aria-label="direction"
        name="direction"
        value={direction}
        onChange={handleDirectionChange}
        row
      >
        <FormControlLabel value="up" control={<Radio />} label="Up" />
        <Box sx={{ display: { xs: 'none', sm: 'block' } }}>
          <FormControlLabel value="right" control={<Radio />} label="Right" />
        </Box>
        <FormControlLabel value="down" control={<Radio />} label="Down" />
        <Box sx={{ display: { xs: 'none', sm: 'block' } }}>
          <FormControlLabel value="left" control={<Radio />} label="Left" />
        </Box>
      </RadioGroup>
      <Box sx={{ position: 'relative', marginTop: theme.spacing(3), height: 300 }}>
        <SpeedDial
          sx={{
            position: 'absolute',
            '&.MuiSpeedDial-directionUp, &.MuiSpeedDial-directionLeft': {
              bottom: theme.spacing(2),
              right: theme.spacing(2),
            },
            '&.MuiSpeedDial-directionDown, &.MuiSpeedDial-directionRight': {
              top: theme.spacing(2),
              left: theme.spacing(2),
            },
          }}
          ariaLabel="SpeedDial example"
          hidden={hidden}
          icon={<SpeedDialIcon />}
          onClose={handleClose}
          onOpen={handleOpen}
          open={open}
          direction={direction}
        >
          {actions.map(action => (
            <SpeedDialAction
              key={action.name}
              icon={action.icon}
              tooltipTitle={action.name}
              onClick={handleClose}
            />
          ))}
        </SpeedDial>
      </Box>
    </div>
  );
}
