// material-ui
import { Alert, Button, Grid, Stack, TextField } from '@mui/material';

// project imports
import { useNotifications } from 'contexts/NotificationContext';
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';
import AnimateButton from 'ui-component/extended/AnimateButton';
import { useAdvancedForm } from '@/hooks/enterprise';
import type { IValidationRule } from '@/types/common';

// ==============================|| FORM VALIDATION - INSTANT FEEDBACK ENTERPRISE ||============================== //

// Enterprise Pattern: Type-safe form interface
interface InstantFeedbackFormData {
  emailInstant: string;
  passwordInstant: string;
}

// Enterprise Pattern: Form validation rules
const validationRules: Partial<Record<keyof InstantFeedbackFormData, IValidationRule[]>> = {
  emailInstant: [
    { type: 'required', message: 'Email is required' },
    { type: 'email', message: 'Enter a valid email' },
  ],
  passwordInstant: [
    { type: 'required', message: 'Password is required' },
    { type: 'minLength', value: 8, message: 'Password should be of minimum 8 characters length' },
  ],
};

const InstantFeedback = () => {
  // Enterprise Pattern: Use modern notification system
  const notifications = useNotifications();

  // Enterprise Pattern: Advanced form hook with validation
  const form = useAdvancedForm<InstantFeedbackFormData>({
    initialValues: {
      emailInstant: '',
      passwordInstant: '',
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Enterprise Pattern: Simulated API call (replace with actual API)
        await new Promise((resolve) => setTimeout(resolve, 1000));
        console.log('Instant feedback form submitted:', { ...values, passwordInstant: '***' });

        // Success notification
        notifications.showNotification({
          message: 'On Leave - Submit Success',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' },
          close: false,
        });
      } catch (error) {
        // Error notification
        notifications.showNotification({
          message: 'Form submission failed. Please try again.',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      }
    },
  });

  return (
    <MainCard title="On Leave">
      <form onSubmit={form.handleSubmit()}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              id="emailInstant"
              name="emailInstant"
              label="Email"
              value={form.values.emailInstant}
              onChange={form.handleChange('emailInstant')}
              onBlur={form.handleBlur('emailInstant')}
              error={Boolean(form.touched.emailInstant && form.errors.emailInstant)}
              helperText={form.touched.emailInstant && form.errors.emailInstant}
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              id="passwordInstant"
              name="passwordInstant"
              label="Password"
              type="password"
              value={form.values.passwordInstant}
              onChange={form.handleChange('passwordInstant')}
              onBlur={form.handleBlur('passwordInstant')}
              error={Boolean(form.touched.passwordInstant && form.errors.passwordInstant)}
              helperText={form.touched.passwordInstant && form.errors.passwordInstant}
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
                  {form.isSubmitting ? 'Submitting...' : 'Submit'}
                </Button>
              </AnimateButton>
            </Stack>
          </Grid>
        </Grid>
      </form>
    </MainCard>
  );
};

export default InstantFeedback;
