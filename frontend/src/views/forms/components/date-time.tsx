'use client';

// material-ui
import { Grid  } from '@mui/material';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import React from 'react';

// project imports
import CustomDateTime from 'components/forms/components/DateTime/CustomDateTime';
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

// ==============================|| DATETIME ||============================== //

const DateTime = () => {
  const [valueBasic, setValueBasic] = React.useState<Date | null>(new Date());

  return (
    <MainCard
      title="Date & Time"
      secondary={
        <SecondaryAction link="https://next.material-ui.com/components/date-time-picker/" />
      }
    >
      <Grid container spacing={gridSpacing}>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Basic Datetime Picker">
            <LocalizationProvider dateAdapter={AdapterDateFns}>
              <DateTimePicker
                slotProps={{ textField: { fullWidth: true } }}
                label="Date & Time"
                value={valueBasic}
                onChange={(newValue: Date | null) => {
                  setValueBasic(newValue);
                }}
              />
            </LocalizationProvider>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Disabled">
            <LocalizationProvider dateAdapter={AdapterDateFns}>
              <DateTimePicker
                slotProps={{ textField: { fullWidth: true } }}
                label="Date & Time"
                value={valueBasic}
                onChange={newValue => {
                  setValueBasic(newValue);
                }}
                disabled
              />
            </LocalizationProvider>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Mobile Mode">
            <CustomDateTime />
          </SubCard>
        </Grid>
      </Grid>
    </MainCard>
  );
};

export default DateTime;
