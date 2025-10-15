import HighlightOffTwoToneIcon from '@mui/icons-material/HighlightOffTwoTone';
import { Button,
  Dialog,
  FormControl,
  FormControlLabel,
  IconButton,
  Grid,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  FormHelperText,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useFormik } from 'formik';

// material-ui

// third-party
import * as yup from 'yup';

// project imports
import { useNotifications } from 'contexts/NotificationContext';
// Note: useCart import removed - not used in this component
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';
import AnimateButton from 'ui-component/extended/AnimateButton';

// Using Context API

// assets

// Transition component removed - using default Dialog animations

const validationSchema = yup.object({
  type: yup.string().required('Card type selection is required'),
  method: yup.string().required('Card selection is required'),
  bank: yup.string().required('Bank is required'),
  number: yup.string().required('Card number is required'),
  expired: yup.string().required('Expiry date is required'),
  cvv: yup.string().required('CVV is required'),
});

// ==============================|| CHECKOUT PAYMENT - ADD NEW CARDS ||============================== //

const AddPaymentCard = ({ open, handleClose }: { open: boolean; handleClose: () => void }) => {
  const theme = useTheme();
  
  // Modern Context API implementation
  const notificationContext = useNotifications();
  
  // Use Context API directly

  const formik = useFormik({
    enableReinitialize: true,
    initialValues: {
      number: '',
      type: '',
      expired: '',
      cvv: '',
      bank: '',
      method: '',
    },
    validationSchema,
    onSubmit: _values => {
      handleClose();
      
      // Use Context API directly
      notificationContext.showNotification({
        message: 'Payment Card Add Success',
        variant: 'success',
        alert: {
          color: 'success',
          variant: 'filled',
        },
        close: true,
      });
    },
  });

  return (
    <Dialog
      open={open}
      keepMounted
      onClose={handleClose}
      aria-labelledby="alert-dialog-slide-title"
      aria-describedby="alert-dialog-slide-description"
      sx={{
        '& .MuiDialog-paper': {
          p: 0,
        },
      }}
    >
      {open && (
        <MainCard
          title="Add Payment Card"
          secondary={
            <IconButton onClick={handleClose} size="large" aria-label="close add card tabs">
              <HighlightOffTwoToneIcon fontSize="small" />
            </IconButton>
          }
        >
          <form onSubmit={formik.handleSubmit}>
            <Grid container spacing={gridSpacing}>
              <Grid size={{ xs: 12 }}>
                <FormControl>
                  <RadioGroup
                    row
                    aria-label="type"
                    value={formik.values.type}
                    onChange={formik.handleChange}
                    name="type"
                    id="type"
                  >
                    <FormControlLabel
                      value="visa"
                      control={
                        <Radio
                          sx={{
                            color: theme.palette.primary.main,
                            '&.Mui-checked': { color: theme.palette.primary.main },
                          }}
                        />
                      }
                      label="Visa"
                    />
                    <FormControlLabel
                      value="mastercard"
                      control={
                        <Radio
                          sx={{
                            color: theme.palette.secondary.main,
                            '&.Mui-checked': { color: theme.palette.secondary.main },
                          }}
                        />
                      }
                      label="Mastercard"
                    />
                  </RadioGroup>
                </FormControl>
                {formik.errors.type && (
                  <FormHelperText error id="standard-weight-helper-text-name-login">
                    {formik.errors.type}
                  </FormHelperText>
                )}
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField
                  fullWidth
                  id="bank"
                  name="bank"
                  label="Bank"
                  value={formik.values.bank}
                  onChange={formik.handleChange}
                  error={!!(formik.touched.bank && formik.errors.bank)}
                  helperText={formik.touched.bank && formik.errors.bank}
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField
                  fullWidth
                  id="number"
                  name="number"
                  label="Card Number"
                  value={formik.values.number}
                  onChange={formik.handleChange}
                  error={!!(formik.touched.number && formik.errors.number)}
                  helperText={formik.touched.number && formik.errors.number}
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField
                  fullWidth
                  id="expired"
                  name="expired"
                  label="Expiry Date"
                  value={formik.values.expired}
                  onChange={formik.handleChange}
                  error={!!(formik.touched.expired && formik.errors.expired)}
                  helperText={formik.touched.expired && formik.errors.expired}
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField
                  fullWidth
                  id="cvv"
                  name="cvv"
                  label="CVV"
                  value={formik.values.cvv}
                  onChange={formik.handleChange}
                  error={!!(formik.touched.cvv && formik.errors.cvv)}
                  helperText={formik.touched.cvv && formik.errors.cvv}
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <FormControl>
                  <RadioGroup
                    row
                    aria-label="method"
                    value={formik.values.method}
                    onChange={formik.handleChange}
                    name="method"
                    id="method"
                  >
                    <FormControlLabel
                      value="credit"
                      control={
                        <Radio
                          sx={{
                            color: theme.palette.primary.main,
                            '&.Mui-checked': { color: theme.palette.primary.main },
                          }}
                        />
                      }
                      label="Credit Card"
                    />
                    <FormControlLabel
                      value="Debit Card"
                      control={
                        <Radio
                          sx={{
                            color: theme.palette.secondary.main,
                            '&.Mui-checked': { color: theme.palette.secondary.main },
                          }}
                        />
                      }
                      label="debit"
                    />
                    <FormControlLabel
                      value="net-banking"
                      control={
                        <Radio
                          sx={{
                            color: theme.palette.secondary.main,
                            '&.Mui-checked': { color: theme.palette.secondary.main },
                          }}
                        />
                      }
                      label="Net Banking"
                    />
                  </RadioGroup>
                </FormControl>
                {formik.errors.method && (
                  <FormHelperText error id="standard-weight-helper-text-name-login">
                    {formik.errors.method}
                  </FormHelperText>
                )}
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Stack direction="row" spacing={1} justifyContent="flex-end">
                  <Button color="error" onClick={handleClose}>
                    Cancel
                  </Button>
                  <AnimateButton>
                    <Button variant="contained" type="submit">
                      Submit
                    </Button>
                  </AnimateButton>
                </Stack>
              </Grid>
            </Grid>
          </form>
        </MainCard>
      )}
    </Dialog>
  );
};

export default AddPaymentCard;
