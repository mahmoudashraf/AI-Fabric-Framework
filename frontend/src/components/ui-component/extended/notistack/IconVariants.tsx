'use client';

// material-ul
import { Button, FormControl, Radio, FormControlLabel, RadioGroup } from '@mui/material';

// third-party
import { enqueueSnackbar } from 'notistack';
import { useState, ChangeEvent } from 'react';

// project import
import { useNotifications } from 'contexts/NotificationContext';
import SubCard from 'ui-component/cards/SubCard';

// ==============================|| NOTISTACK - CUSTOM ICON ||============================== //

export default function IconVariants() {
  const [value, setValue] = useState('usedefault');
  
  // Use Context API for notifications
  const notificationContext = useNotifications();
  
  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    setValue((event.target as HTMLInputElement).value);
  };

  return (
    <SubCard title="With Icons">
      <FormControl>
        <RadioGroup
          row
          aria-labelledby="demo-row-radio-buttons-group-label"
          value={value}
          onChange={handleChange}
          name="row-radio-buttons-group"
        >
          <FormControlLabel value="usedefault" control={<Radio />} label="Use Default" />
          <FormControlLabel value="useemojis" control={<Radio />} label="Use Emojis" />
          <FormControlLabel value="hide" control={<Radio />} label="Hide" />
        </RadioGroup>
      </FormControl>
      <Button
        variant="contained"
        fullWidth
        sx={{ marginBlockStart: 2 }}
        onClick={() => {
          enqueueSnackbar('Your notification here', { variant: 'info' });
          
          notificationContext.showNotification({
            message: `Icon variant: ${value}`,
            variant: 'info',
            alert: {
              color: 'info',
              variant: 'filled',
            },
            close: true,
          });
        }}
      >
        Show Snackbar
      </Button>
    </SubCard>
  );
}
