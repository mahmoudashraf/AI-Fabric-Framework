// material-ui
import { Box, Slider, Switch, Typography } from '@mui/material';

// third-party
import { format } from 'date-fns';
import { memo } from 'react';

// project-import
import ControlPanelStyled from 'ui-component/third-party/map/ControlPanelStyled';

type Props = {
  startTime: number;
  endTime: number;
  allDays: boolean;
  selectedTime: number;
  onChangeTime: (value: number) => void;
  onChangeAllDays: (value: boolean) => void;
};

// ==============================|| HEATMAP - CONTROL ||============================== //

const ControlPanel = ({
  startTime,
  endTime,
  allDays,
  selectedTime,
  onChangeTime,
  onChangeAllDays,
}: Props) => {
  const day = 24 * 60 * 60 * 1000;
  const days = Math.round((endTime - startTime) / day);
  const selectedDay = Math.round((selectedTime - startTime) / day);

  const handleChangeDays = (value: number) => {
    const daysToAdd = value;
    const newTime = startTime + daysToAdd * day;
    onChangeTime(newTime);
  };

  return (
    <ControlPanelStyled>
      <Box
        sx={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Typography variant="subtitle2">All Days</Typography>
        <Switch
          size="small"
          checked={allDays}
          onChange={event => onChangeAllDays(event.target.checked)}
        />
      </Box>

      <br />

      <Typography
        gutterBottom
        variant="body2"
        sx={{ color: allDays ? 'text.disabled' : 'text.primary' }}
      >
        Each Day: {format(new Date(selectedTime), 'dd MMM yyyy')}
      </Typography>

      <Slider
        min={1}
        step={1}
        max={days}
        disabled={allDays}
        value={selectedDay}
        color="primary"
        onChange={(_event, newValue) => {
          if (typeof newValue === 'number') handleChangeDays(newValue);
        }}
      />
    </ControlPanelStyled>
  );
};

export default memo(ControlPanel);
