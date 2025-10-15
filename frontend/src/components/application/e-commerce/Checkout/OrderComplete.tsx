import KeyboardBackspaceIcon from '@mui/icons-material/KeyboardBackspace';
import { Box,
  Button,
  Dialog,
  Divider,
  Grid,
  Stack,
  Typography,
  useMediaQuery,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Chance } from 'chance';
import Image from 'next/image';
import Link from 'next/link';

// material-ui

// project imports
import PerfectScrollbar from 'react-perfect-scrollbar';

const gridSpacing = 3;
import MainCard from 'ui-component/cards/MainCard';

// third-party

// assets
const completed = '/assets/images/e-commerce/completed.png';

const chance = new Chance();

// ==============================|| CHECKOUT CART - ORDER COMPLETE ||============================== //

const OrderComplete = ({ open }: { open: boolean }) => {
  const theme = useTheme();
  const matchDownMD = useMediaQuery(theme.breakpoints.down('md'));

  return (
    <Dialog
      open={open}
      keepMounted
      aria-labelledby="alert-dialog-slide-title"
      aria-describedby="alert-dialog-slide-description"
      maxWidth="md"
      sx={{
        '& .MuiDialog-paper': {
          p: 0,
        },
      }}
    >
      {open && (
        <MainCard>
          <PerfectScrollbar
            style={{
              overflowX: 'hidden',
              height: 'calc(100vh - 100px)',
            }}
          >
            <Grid
              container
              direction="column"
              spacing={gridSpacing}
              alignItems="center"
              justifyContent="center"
              sx={{ my: 3 }}
            >
              <Grid size={{ xs: 12 }}>
                <Typography variant={matchDownMD ? 'h2' : 'h1'}>Thank you for order!</Typography>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Stack alignItems="center" spacing={2}>
                  <Typography
                    align="center"
                    variant="h4"
                    sx={{ fontWeight: 400, color: 'grey.500' }}
                  >
                    We will send a process notification, before it delivered.
                  </Typography>
                  <Typography variant="body1" align="center">
                    Your order id:{' '}
                    <Typography variant="subtitle1" component="span" color="primary">
                      {chance.guid()}
                    </Typography>
                  </Typography>
                </Stack>
              </Grid>
              <Grid size={{ xs: 12 }} sx={{ m: 3 }}>
                <Box
                  sx={{
                    position: 'relative',
                    width: { xs: '200px', md: '400px' },
                    height: { xs: '112px', md: '223px' },
                  }}
                >
                  <Image src={completed} alt="Order Complete" fill sizes="100vw" />
                </Box>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Stack alignItems="center" spacing={1}>
                  <Typography variant="caption" align="center">
                    If you have any query or questions regarding purchase items, then fell to get in
                    contact us
                  </Typography>
                  <Typography variant="subtitle1" color="error" sx={{ cursor: 'pointer' }}>
                    {chance.phone()}
                  </Typography>
                </Stack>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Divider />
              </Grid>
              <Grid size={{ xs: 12, sm: 9 }}>
                <Grid
                  direction={matchDownMD ? 'column-reverse' : 'row'}
                  container
                  spacing={3}
                  alignItems={matchDownMD ? '' : 'center'}
                  justifyContent="space-between"
                >
                  <Grid>
                    <Button
                      component={Link}
                      href="/apps/e-commerce/products"
                      variant="text"
                      startIcon={<KeyboardBackspaceIcon />}
                    >
                      Continue Shopping
                    </Button>
                  </Grid>
                  <Grid>
                    <Button
                      component={Link}
                      href="/apps/e-commerce/products"
                      variant="contained"
                      fullWidth
                    >
                      Download Invoice
                    </Button>
                  </Grid>
                </Grid>
              </Grid>
            </Grid>
          </PerfectScrollbar>
        </MainCard>
      )}
    </Dialog>
  );
};

export default OrderComplete;
