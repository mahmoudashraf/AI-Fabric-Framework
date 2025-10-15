'use client';

// material-ui
import { Button, Stack, Checkbox } from '@mui/material';

// third-party
import { enqueueSnackbar } from 'notistack';
import React from 'react';

// project import
import SubCard from 'ui-component/cards/SubCard';

// ==============================|| NOTISTACK - PREVENT DUPLICATE ||============================== //

export default function PreventDuplicate() {
  const [checked, setChecked] = React.useState(true);

  const handleChangeCheck = (event: React.ChangeEvent<HTMLInputElement>) => {
    setChecked(event.target.checked);
  };

  return (
    <SubCard title="Prevent Duplicate">
      <Stack
        flexDirection={'row'}
        gap={1}
        justifyContent={'center'}
        alignItems={'center'}
        flexWrap="wrap"
      >
        <Checkbox
          checked={checked}
          onChange={handleChangeCheck}
          inputProps={{ 'aria-label': 'controlled' }}
        />
        Prevent duplicate
        <Button
          variant="outlined"
          fullWidth
          sx={{ marginBlockStart: 2 }}
          onClick={() =>
            enqueueSnackbar('You only see me once.', {
              preventDuplicate: checked ? true : false,
              variant: 'info',
            })
          }
        >
          Show snackbar
        </Button>
      </Stack>
    </SubCard>
  );
}
