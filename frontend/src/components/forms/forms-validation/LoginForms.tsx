// material-ui
import LinkIcon from '@mui/icons-material/Link';
import { Alert, Button, Grid, Stack, TextField } from '@mui/material';

// project imports
import { gridSpacing } from 'constants/index';
import { useNotifications } from 'contexts/NotificationContext';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import AnimateButton from 'ui-component/extended/AnimateButton';
import { useAdvancedForm } from '@/hooks/enterprise';
import type { IValidationRule } from '@/types/common';

// ==============================|| FORM VALIDATION - LOGIN ENTERPRISE ||============================== //

// Enterprise Pattern: Type-safe form interface
interface LoginFormData {
  email: string;
  password: string;
}

// Enterprise Pattern: Form validation rules
const validationRules: Partial<Record<keyof LoginFormData, IValidationRule[]>> = {
  email: [
    { type: 'required', message: 'Email is required' },
    { type: 'email', message: 'Enter a valid email' },
  ],
  password: [
    { type: 'required', message: 'Password is required' },
    { type: 'minLength', value: 8, message: 'Password should be of minimum 8 characters length' },
  ],
};

const LoginForms = () => {
  const notifications = useNotifications();

  // Enterprise Pattern: Advanced form hook with validation
  const form = useAdvancedForm<LoginFormData>({
    initialValues: {
      email: '',
      password: '',
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Enterprise Pattern: Simulated API call (replace with actual API)
        await new Promise((resolve) => setTimeout(resolve, 1000));
        console.log('Login form submitted:', { ...values, password: '***' });

        // Success notification
        notifications.showNotification({
          message: 'Submit Success',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' },
          close: false,
        });
      } catch (error) {
        // Error notification
        notifications.showNotification({
          message: 'Login failed. Please try again.',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      }
    },
  });

  return (
    <MainCard
      title="On Submit"
      secondary={
        <SecondaryAction
          icon={<LinkIcon fontSize="small" />}
          link="https://formik.org/docs/examples/with-material-ui"
        />
      }
    >
      <form onSubmit={form.handleSubmit()}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              id="email"
              name="email"
              label="Email"
              value={form.values.email}
              onChange={form.handleChange('email')}
              onBlur={form.handleBlur('email')}
              error={Boolean(form.touched.email && form.errors.email)}
              helperText={form.touched.email && form.errors.email}
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              id="password"
              name="password"
              label="Password"
              type="password"
              value={form.values.password}
              onChange={form.handleChange('password')}
              onBlur={form.handleBlur('password')}
              error={Boolean(form.touched.password && form.errors.password)}
              helperText={form.touched.password && form.errors.password}
            />
          </Grid>

          {/* Enterprise Pattern: Form status feedback */}
          {Object.keys(form.errors).length > 0 && Object.keys(form.touched).length > 0 && (
            <Grid size={{ xs: 12 }}>
              <Alert severity="error">Please fix the errors above before submitting</Alert>
            </Grid>
          )}

          <Grid size={{ xs: 12 }}>
            <Stack direction="row" justifyContent="flex-end">
              <AnimateButton>
                <Button 
                  variant="contained" 
                  type="submit"
                  disabled={form.isSubmitting || !form.isValid}
                >
                  {form.isSubmitting ? 'Submitting...' : 'Verify & Submit'}
                </Button>
              </AnimateButton>
            </Stack>
          </Grid>
        </Grid>
      </form>
    </MainCard>
  );
};

export default LoginForms;
