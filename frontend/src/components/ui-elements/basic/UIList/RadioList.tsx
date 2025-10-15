'use client';
import { Card, CardContent, Grid, Switch, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import React from 'react';

// material-ui

// ================================|| UI LIST - RADIO ||================================ //

export default function RadioList() {
  const theme = useTheme();
  const [sdm, setSdm] = React.useState(true);
  const [notification, setNotification] = React.useState(false);

  return (
    <Card
      sx={{
        backgroundColor:
          theme.palette.mode === 'dark' ? theme.palette.dark[800] : theme.palette.primary.light,
        mb: 2,
        mt: 2,
      }}
    >
      <CardContent>
        <Grid container spacing={3} direction="column">
          <Grid>
            <Grid container alignItems="center" justifyContent="space-between">
              <Grid>
                <Typography variant="subtitle1">Start DND Mode</Typography>
              </Grid>
              <Grid>
                <Switch
                  size="small"
                  color="primary"
                  checked={sdm}
                  onChange={e => setSdm(e.target.checked)}
                  name="sdm"
                />
              </Grid>
            </Grid>
          </Grid>
          <Grid>
            <Grid container alignItems="center" justifyContent="space-between">
              <Grid>
                <Typography variant="subtitle1">Allow Notifications</Typography>
              </Grid>
              <Grid>
                <Switch
                  size="small"
                  checked={notification}
                  onChange={e => setNotification(e.target.checked)}
                  name="sdm"
                />
              </Grid>
            </Grid>
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );
}
