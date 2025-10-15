'use client';

import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import { Box,
  Button,
  FormControl,
  FormHelperText,
  Grid,
  IconButton,
  InputAdornment,
  InputLabel,
  OutlinedInput,
  Typography,
 } from '@mui/material';
import { Formik } from 'formik';
import React, { useEffect } from 'react';

// third party
import * as Yup from 'yup';

// project imports
import useAuth from 'hooks/useAuth';
import useScriptRef from 'hooks/useScriptRef';
// Using Context API
import { useNotifications } from 'contexts/NotificationContext';
import { StringColorProps } from 'types';
import AnimateButton from 'ui-component/extended/AnimateButton';
import { strengthColor, strengthIndicator } from 'utils/password-strength';

// assets

// types

// ========================|| FIREBASE - RESET PASSWORD ||======================== //

const AuthResetPassword = ({ ...others }) => {
  const scriptedRef = useScriptRef();
  const notificationContext = useNotifications();
  const [showPassword, setShowPassword] = React.useState(false);
  const [strength, setStrength] = React.useState(0);
  const [level, setLevel] = React.useState<StringColorProps>();

  const { isLoggedIn } = useAuth();

  const handleClickShowPassword = () => {
    setShowPassword(!showPassword);
  };

  const handleMouseDownPassword = (event: React.SyntheticEvent) => {
    event.preventDefault();
  };

  const changePassword = (value: string) => {
    const temp = strengthIndicator(value);
    setStrength(temp);
    setLevel(strengthColor(temp));
  };

  useEffect(() => {
    changePassword('');
  }, []);

  return (
    <Formik
      initialValues={{
        password: '',
        confirmPassword: '',
        submit: null,
      }}
      validationSchema={Yup.object().shape({
        password: Yup.string().max(255).required('Password is required'),
        confirmPassword: Yup.string()
          .required('Confirm Password is required')
          .test(
            'confirmPassword',
            'Both Password must be match!',
            (confirmPassword, yup) => yup.parent.password === confirmPassword
          ),
      })}
      onSubmit={async (_values, { setErrors, setStatus, setSubmitting }) => {
        try {
          // password reset
          if (scriptedRef.current) {
            setStatus({ success: true });
            setSubmitting(false);

            notificationContext.showNotification({
              message: 'Successfully reset password.',
              variant: 'success',
              alert: {
                color: 'success',
                variant: 'filled',
              },
              close: true,
            });

            setTimeout(() => {
              window.location.replace(isLoggedIn ? '/pages/authentication/auth3/login' : '/login');
            }, 1500);
          }
        } catch (err: any) {
          console.error(err);
          if (scriptedRef.current) {
            setStatus({ success: false });
            setErrors({ submit: err.message });
            setSubmitting(false);
          }
        }
      }}
    >
      {({ errors, handleBlur, handleChange, handleSubmit, isSubmitting, touched, values }) => (
        <form noValidate onSubmit={handleSubmit} {...others}>
          <FormControl
            fullWidth
            error={Boolean(touched.password && errors.password)}
          >
            <InputLabel htmlFor="outlined-adornment-password-reset">Password</InputLabel>
            <OutlinedInput
              id="outlined-adornment-password-reset"
              type={showPassword ? 'text' : 'password'}
              value={values.password}
              name="password"
              onBlur={handleBlur}
              onChange={e => {
                handleChange(e);
                changePassword(e.target.value);
              }}
              endAdornment={
                <InputAdornment position="end">
                  <IconButton
                    aria-label="toggle password visibility"
                    onClick={handleClickShowPassword}
                    onMouseDown={handleMouseDownPassword}
                    edge="end"
                    size="large"
                  >
                    {showPassword ? <Visibility /> : <VisibilityOff />}
                  </IconButton>
                </InputAdornment>
              }
              inputProps={{}}
            />
          </FormControl>
          {touched.password && errors.password && (
            <FormControl fullWidth>
              <FormHelperText error id="standard-weight-helper-text-reset">
                {errors.password}
              </FormHelperText>
            </FormControl>
          )}
          {strength !== 0 && (
            <FormControl fullWidth>
              <Box sx={{ mb: 2 }}>
                <Grid container spacing={2} alignItems="center">
                  <Grid>
                    <Box
                      style={{ backgroundColor: level?.color }}
                      sx={{
                        width: 85,
                        height: 8,
                        borderRadius: '7px',
                      }}
                    />
                  </Grid>
                  <Grid>
                    <Typography variant="subtitle1" fontSize="0.75rem">
                      {level?.label}
                    </Typography>
                  </Grid>
                </Grid>
              </Box>
            </FormControl>
          )}

          <FormControl
            fullWidth
            error={Boolean(touched.confirmPassword && errors.confirmPassword)}
          >
            <InputLabel htmlFor="outlined-adornment-confirm-password">Confirm Password</InputLabel>
            <OutlinedInput
              id="outlined-adornment-confirm-password"
              type="password"
              value={values.confirmPassword}
              name="confirmPassword"
              label="Confirm Password"
              onBlur={handleBlur}
              onChange={handleChange}
              inputProps={{}}
            />
          </FormControl>

          {touched.confirmPassword && errors.confirmPassword && (
            <FormControl fullWidth>
              <FormHelperText error id="standard-weight-helper-text-confirm-password">
                {' '}
                {errors.confirmPassword}{' '}
              </FormHelperText>
            </FormControl>
          )}

          {errors.submit && (
            <Box
              sx={{
                mt: 3,
              }}
            >
              <FormHelperText error>{errors.submit}</FormHelperText>
            </Box>
          )}
          <Box
            sx={{
              mt: 1,
            }}
          >
            <AnimateButton>
              <Button
                disableElevation
                disabled={isSubmitting}
                fullWidth
                size="large"
                type="submit"
                variant="contained"
                color="secondary"
              >
                Reset Password
              </Button>
            </AnimateButton>
          </Box>
        </form>
      )}
    </Formik>
  );
};

export default AuthResetPassword;
