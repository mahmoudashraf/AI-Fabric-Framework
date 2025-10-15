'use client';

// material-ul
import { Button, Checkbox } from '@mui/material';

// third-party
import { enqueueSnackbar } from 'notistack';
import { useState, ChangeEvent } from 'react';

// project import
import { useNotifications } from 'contexts/NotificationContext';

// Using Context API
import SubCard from 'ui-component/cards/SubCard';

// ==============================|| NOTISTACK - DENSE ||============================== //

export default function Dense() {
  const [checked, setChecked] = useState(false);
  
  // Using Context API
  const notificationContext = useNotifications();
  
  // Use Context API directly

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    setChecked(event.target.checked);
    notificationContext.showNotification({
      message: `Dense ${event.target.checked ? 'enabled' : 'disabled'}`,
      variant: 'info',
      alert: {
        color: 'info',
        variant: 'filled',
      },
      close: true,
    });
  };

  return (
    <SubCard title="Dense">
      <Checkbox
        checked={checked}
        onChange={handleChange}
        inputProps={{ 'aria-label': 'controlled' }}
      />
      Dense margins
      <Button
        variant="outlined"
        fullWidth
        sx={{ marginBlockStart: 2 }}
        onClick={() => enqueueSnackbar('Your notification here')}
      >
        Show snackbar
      </Button>
    </SubCard>
  );
}
