'use client';

import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
import {
  Timeline,
  TimelineDot,
  TimelineItem,
  TimelineConnector,
  TimelineContent,
  TimelineOppositeContent,
  TimelineSeparator,
} from '@mui/lab';
import { Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  Divider,
  Grid,
  List,
  ListItem,
  ListItemText,
  Slide,
  SlideProps,
  Stack,
  TextField,
  Typography,
 } from '@mui/material';
import { useTheme, Theme } from '@mui/material/styles';
import { forwardRef, useState } from 'react';

// material-ui

// project imports
import { gridSpacing } from 'constants/index';
import SubCard from 'ui-component/cards/SubCard';
import AnimateButton from 'ui-component/extended/AnimateButton';

// assets

const listBoxSX = {
  bgcolor: (theme: Theme) => theme.palette.background.default,
  py: 0,
};

const dotSX = {
  p: 0,
  '& > svg': {
    width: 14,
    height: 14,
  },
  display: { xs: 'none', md: 'flex' },
};

// tab animation
const Transition = forwardRef((props: SlideProps, ref) => (
  <Slide direction="left" ref={ref} {...props} />
));

const Status = () => {
  const theme = useTheme();

  // toggle write a review dialog
  const [open, setOpen] = useState(false);
  const handleClickOpenDialog = () => {
    setOpen(true);
  };

  const handleCloseDialog = () => {
    setOpen(false);
  };

  return (
    <SubCard title="ORDER STATUS">
      <Grid container spacing={gridSpacing}>
        <Grid size={{ xs: 12 }}>
          <Grid container spacing={0}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Grid container spacing={1}>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="h5">Order Place Date</Typography>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="body2">10th Mar, 2021</Typography>
                </Grid>
              </Grid>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Grid container spacing={1}>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="h5">Order Status</Typography>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="body2">Processing</Typography>
                </Grid>
              </Grid>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Grid container spacing={1}>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="h5">Delivery Option</Typography>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="body2">Fedex Express Delivery</Typography>
                </Grid>
              </Grid>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Grid container spacing={1}>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="h5">Payment</Typography>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="body2">Credit Card</Typography>
                </Grid>
              </Grid>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Grid container spacing={1}>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="h5">Order Amount</Typography>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="body2">$90,020</Typography>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
        <Grid>
          <Timeline
            sx={{
              '& > li': {
                mb: 2.75,
                [theme.breakpoints.down('md')]: {
                  flexDirection: 'column',
                  '& > div': {
                    px: 0,
                  },
                  '& > div:first-of-type': {
                    textAlign: 'left',
                  },
                },
              },
              [theme.breakpoints.down('md')]: {
                p: 0,
              },
            }}
          >
            <TimelineItem>
              <TimelineOppositeContent>
                <Typography variant="h6">Order Placed</Typography>
                <Typography variant="body2">12 jun</Typography>
              </TimelineOppositeContent>
              <TimelineSeparator>
                <TimelineDot color="primary" sx={dotSX}>
                  <FiberManualRecordIcon />
                </TimelineDot>
                <TimelineConnector sx={{ bgcolor: 'primary.main' }} />
              </TimelineSeparator>
              <TimelineContent sx={{ flex: 3 }}>
                <List sx={listBoxSX}>
                  <ListItem>
                    <ListItemText primary="The order was validated." />
                  </ListItem>
                  <Divider />
                  <ListItem>
                    <ListItemText primary="The order was placed." />
                  </ListItem>
                  <Divider />
                  <ListItem>
                    <ListItemText primary="The order was placed." />
                  </ListItem>
                </List>
              </TimelineContent>
            </TimelineItem>
            <TimelineItem>
              <TimelineOppositeContent>
                <Typography variant="h6">Order Processing</Typography>
                <Typography variant="body2">14 jun</Typography>
              </TimelineOppositeContent>
              <TimelineSeparator>
                <TimelineDot color="primary" sx={dotSX}>
                  <FiberManualRecordIcon />
                </TimelineDot>
                <TimelineConnector sx={{ bgcolor: 'grey.400' }} />
              </TimelineSeparator>
              <TimelineContent sx={{ flex: 3 }}>
                <List sx={listBoxSX}>
                  <ListItem>
                    <ListItemText primary="Payment transaction [method: Credit Card, type: sale, amount: $90,020, status: Processing ]" />
                  </ListItem>
                </List>
              </TimelineContent>
            </TimelineItem>
            <TimelineItem>
              <TimelineOppositeContent>
                <Typography variant="h6">Order Shipping</Typography>
                <Typography variant="body2">16 Jun</Typography>
              </TimelineOppositeContent>
              <TimelineSeparator>
                <TimelineDot sx={dotSX}>
                  <FiberManualRecordIcon />
                </TimelineDot>
                <TimelineConnector sx={{ bgcolor: 'grey.400' }} />
              </TimelineSeparator>
              <TimelineContent sx={{ flex: 3 }}>
                <List sx={listBoxSX}>
                  <ListItem>
                    <ListItemText primary="Sent a notification to the client by e-mail." />
                  </ListItem>
                </List>
              </TimelineContent>
            </TimelineItem>
            <TimelineItem>
              <TimelineOppositeContent>
                <Typography variant="h6">Order Delivered</Typography>
                <Typography variant="body2">17 Jun</Typography>
              </TimelineOppositeContent>
              <TimelineSeparator>
                <TimelineDot sx={dotSX}>
                  <FiberManualRecordIcon />
                </TimelineDot>
                <TimelineConnector sx={{ bgcolor: 'grey.400' }} />
              </TimelineSeparator>
              <TimelineContent sx={{ flex: 3 }}>
                <List sx={listBoxSX}>
                  <ListItem>
                    <ListItemText primary="Order Delivered" />
                  </ListItem>
                </List>
              </TimelineContent>
            </TimelineItem>
          </Timeline>
          <Grid container spacing={1} justifyContent="center">
            <Grid>
              <Box sx={{ display: { xs: 'block', md: 'none' } }}>
                <Button variant="contained" onClick={handleClickOpenDialog}>
                  Write a Review
                </Button>
                <Dialog
                  open={open}
                  TransitionComponent={Transition as any}
                  keepMounted
                  onClose={handleCloseDialog}
                  sx={{
                    '&>div:nth-of-type(3)': {
                      justifyContent: 'flex-end',
                      '&>div': {
                        m: 0,
                        borderRadius: '0px',
                        maxWidth: 450,
                        maxHeight: '100%',
                        height: '100vh',
                      },
                    },
                  }}
                >
                  {open && (
                    <>
                      <DialogContent>
                        <Grid container spacing={1}>
                          <Grid size={{ xs: 12 }}>
                            <TextField
                              id="outlined-basic1"
                              fullWidth
                              multiline
                              rows={10}
                              label="Write a Review"
                            />
                          </Grid>
                          <Grid size={{ xs: 12 }} />
                        </Grid>
                      </DialogContent>
                      <DialogActions>
                        <AnimateButton>
                          <Button variant="contained">Post Review</Button>
                        </AnimateButton>
                        <Button variant="text" onClick={handleCloseDialog} color="secondary">
                          Close
                        </Button>
                      </DialogActions>
                    </>
                  )}
                </Dialog>
              </Box>
            </Grid>
          </Grid>
        </Grid>
        <Grid>
          <Box sx={{ display: { xs: 'none', md: 'block' } }}>
            <Grid container spacing={1}>
              <Grid size={{ xs: 12 }}>
                <TextField
                  id="outlined-basic2"
                  fullWidth
                  multiline
                  rows={10}
                  label="Write a Review"
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Stack direction="row">
                  <AnimateButton>
                    <Button variant="contained">Post Review</Button>
                  </AnimateButton>
                </Stack>
              </Grid>
            </Grid>
          </Box>
        </Grid>
      </Grid>
    </SubCard>
  );
};

export default Status;
