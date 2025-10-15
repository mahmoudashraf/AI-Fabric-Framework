'use client';

import AddOutlinedIcon from '@mui/icons-material/AddOutlined';
import RemoveOutlinedIcon from '@mui/icons-material/RemoveOutlined';
import { Button, Typography, Stack } from '@mui/material';
import { enqueueSnackbar } from 'notistack';
import { useState } from 'react';

// material-ul

// third-party

// project import
import { useNotifications } from 'contexts/NotificationContext';
import SubCard from 'ui-component/cards/SubCard';

//asset

// ==============================|| NOTISTACK - MAXIMUM SNACKBAR ||============================== //

export default function MaxSnackbar() {
  const width = { minWidth: 'auto' };

  const { showNotification } = useNotifications();
  const [value, setValue] = useState<number>(3);

  const handlerMaxStack = () => {
    enqueueSnackbar('Your notification here', { variant: 'info' });
    showNotification({
      message: 'Your notification here',
      variant: 'info',
      maxStack: value,
    });
  };

  return (
    <SubCard title="Maximum snackbars">
      <Stack justifyContent={'space-between'} flexDirection={'row'}>
        <Button
          variant="outlined"
          size="small"
          sx={width}
          disabled={value === 0}
          onClick={() => setValue(prev => prev - 1)}
        >
          <RemoveOutlinedIcon />
        </Button>
        <Typography variant="body1">stack up to {value}</Typography>
        <Button
          variant="outlined"
          size="small"
          sx={width}
          disabled={value === 4}
          onClick={() => setValue(prev => prev + 1)}
        >
          <AddOutlinedIcon />
        </Button>
      </Stack>
      <Button
        variant="contained"
        fullWidth
        sx={{ marginBlockStart: 2 }}
        onClick={() => handlerMaxStack()}
      >
        Show Snackbar
      </Button>
    </SubCard>
  );
}
