import { Card, CardContent, Grid, Typography, useTheme  } from '@mui/material';
import React from 'react';

// material-ui

// project imports
import { gridSpacing } from 'constants/index';

// types
import { History } from 'types/chat';
import { UserProfile } from 'types/user-profile';

// ==============================|| CHAT MESSAGE HISTORY ||============================== //

interface ChartHistoryProps {
  data: History[];
  user: UserProfile;
}

const ChartHistory = ({ data, user }: ChartHistoryProps) => {
  const theme = useTheme();
  
  return (
  <Grid size={{ xs: 12 }}>
    <Grid container spacing={gridSpacing}>
      {data.map((history, index) => (
        <React.Fragment key={index}>
          {history.from !== user.name ? (
            <Grid size={{ xs: 12 }}>
              <Grid container spacing={gridSpacing}>
                <Grid size={2} />
                <Grid size={10}>
                  <Card
                    sx={{
                      display: 'inline-block',
                      float: 'right',
                      bgcolor:
                        theme.palette.mode === 'dark' ? 'grey.600' : theme.palette.primary.light,
                    }}
                  >
                    <CardContent
                      sx={{ p: 2, pb: '16px !important', width: 'fit-content', ml: 'auto' }}
                    >
                      <Grid container spacing={1}>
                        <Grid size={{ xs: 12 }}>
                          <Typography
                            variant="body2"
                            color={theme.palette.mode === 'dark' ? 'dark.900' : ''}
                          >
                            {history.text}
                          </Typography>
                        </Grid>
                        <Grid size={{ xs: 12 }}>
                          <Typography
                            align="right"
                            variant="subtitle2"
                            color={theme.palette.mode === 'dark' ? 'dark.900' : ''}
                          >
                            {history.time}
                          </Typography>
                        </Grid>
                      </Grid>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>
            </Grid>
          ) : (
            <Grid size={{ xs: 12 }}>
              <Grid container spacing={gridSpacing}>
                <Grid size={{ xs: 12, sm: 7 }}>
                  <Card
                    sx={{
                      display: 'inline-block',
                      float: 'left',
                      background:
                        theme.palette.mode === 'dark'
                          ? theme.palette.dark[900]
                          : theme.palette.secondary.light,
                    }}
                  >
                    <CardContent sx={{ p: 2, pb: '16px !important' }}>
                      <Grid container spacing={1}>
                        <Grid size={{ xs: 12 }}>
                          <Typography variant="body2">{history.text}</Typography>
                        </Grid>
                        <Grid size={{ xs: 12 }}>
                          <Typography align="right" variant="subtitle2">
                            {history.time}
                          </Typography>
                        </Grid>
                      </Grid>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>
            </Grid>
          )}
        </React.Fragment>
      ))}
    </Grid>
  </Grid>
  );
};

export default ChartHistory;
