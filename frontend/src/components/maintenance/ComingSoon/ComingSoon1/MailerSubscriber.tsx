// material-ui
import { Box,
  Button,
  FormControl,
  FormHelperText,
  Grid,
  InputLabel,
  OutlinedInput,
 } from '@mui/material';

// third party
import axios from 'axios';
import clsx from 'clsx';
import { Formik } from 'formik';
import * as Yup from 'yup';

// project imports
import useScriptRef from 'hooks/useScriptRef';
import { useNotifications } from 'contexts/NotificationContext';
import { gridSpacing } from 'constants/index';
import AnimateButton from 'ui-component/extended/AnimateButton';

// Using Context API

// ===========================|| MAILER SUBSCRIBER ||=========================== //

const MailerSubscriber = ({ className, ...others }: { className?: string }) => {
  const scriptedRef = useScriptRef();
  
  // Using Context API
  const notificationContext = useNotifications();
  
  // Use Context API directly

  return (
    <Formik
      initialValues={{
        email: '',
        submit: null,
      }}
      validationSchema={Yup.object().shape({
        email: Yup.string().email('Must be a valid email').max(255).required('Email is required'),
      })}
      onSubmit={async (values, { setErrors, setStatus, setSubmitting }) => {
        try {
          const options = {
            headers: {
              'content-type': 'application/json',
            },
          };
          await axios.post('https://yourapicall', { email: values.email }, options);
          
          // Use Context API directly
          notificationContext.showNotification({
            message: 'Success! Please check inbox and confirm.',
            variant: 'success',
            alert: {
              color: 'success',
              variant: 'filled',
            },
            close: true,
          });

          if (scriptedRef.current) {
            setStatus({ success: true });
            setSubmitting(false);
          }
        } catch (err: any) {
          if (scriptedRef.current) {
            setStatus({ success: false });
            setErrors({ submit: err?.message });
            setSubmitting(false);
          }
        }
      }}
    >
      {({ errors, handleBlur, handleChange, handleSubmit, isSubmitting, touched, values }) => (
        <form noValidate onSubmit={handleSubmit} className={clsx(className)} {...others}>
          <Grid container alignItems="center" spacing={gridSpacing}>
            <Grid size={{ xs: 12 }} sx={{ minWidth: 0 }}>
              <FormControl fullWidth error={Boolean(touched.email && errors.email)}>
                <InputLabel htmlFor="outlined-adornment-email-forgot">Email Address</InputLabel>
                <OutlinedInput
                  id="outlined-adornment-email-forgot"
                  type="email"
                  defaultValue={values.email}
                  name="email"
                  onBlur={handleBlur}
                  onChange={handleChange}
                  label="Email Address"
                />
              </FormControl>
            </Grid>
            <Grid>
              <AnimateButton>
                <Button
                  disableElevation
                  disabled={isSubmitting}
                  type="submit"
                  variant="contained"
                  size="large"
                  sx={{
                    px: 2.75,
                    py: 1.5,
                  }}
                >
                  Subscribe
                </Button>
              </AnimateButton>
            </Grid>
          </Grid>
          {touched.email && errors.email && (
            <Box sx={{ mt: 1 }}>
              <FormHelperText error id="standard-weight-helper-text-email-forgot">
                {errors.email}
              </FormHelperText>
            </Box>
          )}
          {errors.submit && (
            <Box sx={{ mt: 3 }}>
              <FormHelperText error>{errors.submit}</FormHelperText>
            </Box>
          )}
        </form>
      )}
    </Formik>
  );
};

export default MailerSubscriber;
