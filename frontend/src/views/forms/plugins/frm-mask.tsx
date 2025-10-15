'use client';

import LinkIcon from '@mui/icons-material/Link';
import { Grid, TextField  } from '@mui/material';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { MobileDateTimePicker } from '@mui/x-date-pickers/MobileDateTimePicker';
import { TimePicker } from '@mui/x-date-pickers/TimePicker';
import { useState } from 'react';

// material-ui

// third-party
import { PatternFormat } from 'react-number-format';

// project imports
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

// assets

// ==============================|| PLUGIN - MASK INPUT ||============================== //

const MaskPage = () => {
  const [date1, setDate1] = useState<Date | null>(new Date());
  const [date2, setDate2] = useState<Date | null>(new Date());

  const [time, setTime] = useState<Date | null>(new Date());
  const [datetime, setDatetime] = useState<Date | null>(new Date());

  return (
    <MainCard
      title="Mask"
      secondary={
        <SecondaryAction
          icon={<LinkIcon fontSize="small" />}
          link="https://www.npmjs.com/package/react-number-format"
        />
      }
    >
      <LocalizationProvider dateAdapter={AdapterDateFns}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12, md: 6 }}>
            <SubCard title="Date">
              <Grid container alignItems="center" spacing={3}>
                <Grid size={{ xs: 12 }}>
                  <DatePicker
                    label="Insert Date 1"
                    value={date1}
                    onChange={newValue => setDate1(newValue)}
                    slotProps={{ textField: { fullWidth: true } }}
                  />
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <DatePicker
                    label="Insert Date 2"
                    value={date2}
                    onChange={newValue => setDate2(newValue)}
                    slotProps={{ textField: { fullWidth: true } }}
                    format="mm-dd-yyyy"
                  />
                </Grid>
              </Grid>
            </SubCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <SubCard title="Time">
              <Grid container alignItems="center" spacing={3}>
                <Grid size={{ xs: 12 }}>
                  <TimePicker
                    ampm={false}
                    openTo="hours"
                    views={['hours', 'minutes', 'seconds']}
                    format="HH:mm:ss"
                    label="Time"
                    value={time}
                    onChange={newValue => {
                      setTime(newValue);
                    }}
                    slotProps={{ textField: { fullWidth: true } }}
                  />
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <MobileDateTimePicker
                    value={datetime}
                    onChange={newValue => {
                      setDatetime(newValue);
                    }}
                    slotProps={{ textField: { fullWidth: true } }}
                  />
                </Grid>
              </Grid>
            </SubCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <SubCard title="Phone no.">
              <Grid container alignItems="center" spacing={3}>
                <Grid size={{ xs: 12 }}>
                  <PatternFormat
                    format="+1 (###) ###-####"
                    mask="_"
                    fullWidth
                    customInput={TextField}
                    label="Phone Number"
                  />
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <PatternFormat
                    format="+91 #### ###-####"
                    mask="_"
                    fullWidth
                    customInput={TextField}
                    label="Contact Number"
                  />
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <PatternFormat
                    format="(##) ####-#####"
                    mask="_"
                    fullWidth
                    customInput={TextField}
                    label="Tel. with Code Area"
                  />
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <PatternFormat
                    format="(###) ### #####"
                    mask="_"
                    fullWidth
                    customInput={TextField}
                    label="US Telephone"
                  />
                </Grid>
              </Grid>
            </SubCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <SubCard title="Network">
              <Grid container alignItems="center" spacing={3}>
                <Grid size={{ xs: 12 }}>
                  <PatternFormat
                    format="###.###.###.###"
                    mask="_"
                    fullWidth
                    customInput={TextField}
                    label="IP Address"
                  />
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <PatternFormat
                    format="####.####.####.####"
                    mask="_"
                    fullWidth
                    customInput={TextField}
                    label="IPV4"
                  />
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <PatternFormat
                    format="####:####:####:#:###:####:####:####"
                    mask="_"
                    fullWidth
                    customInput={TextField}
                    label="IPV6"
                  />
                </Grid>
              </Grid>
            </SubCard>
          </Grid>
        </Grid>
      </LocalizationProvider>
    </MainCard>
  );
};

export default MaskPage;
