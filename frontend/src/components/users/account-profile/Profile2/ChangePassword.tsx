// material-ui
import { Alert, Button, Grid, Stack, TextField } from '@mui/material';

// project imports
import { gridSpacing } from 'constants/index';
import AnimateButton from 'ui-component/extended/AnimateButton';
import { useAdvancedForm } from '@/hooks/enterprise';
import { useNotifications } from 'contexts/NotificationContext';
import type { IValidationRule } from '@/types/common';

// ==============================|| PROFILE 2 - CHANGE PASSWORD ||============================== //

// Enterprise Pattern: Type-safe form interface
interface ChangePasswordFormData {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

// Enterprise Pattern: Form validation rules
const validationRules: Partial<Record<keyof ChangePasswordFormData, IValidationRule[]>> = {
  currentPassword: [{ type: 'required', message: 'Current password is required' }],
  newPassword: [
    { type: 'required', message: 'New password is required' },
    { type: 'minLength', value: 8, message: 'Password must be at least 8 characters' },
    {
      type: 'pattern',
      value: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]/,
      message: 'Password must contain uppercase, lowercase, number and special character',
    },
  ],
  confirmPassword: [
    { type: 'required', message: 'Please confirm your password' },
    {
      type: 'custom',
      validator: (value, formValues) => value === formValues?.newPassword,
      message: 'Passwords do not match',
    },
  ],
};

const ChangePassword = () => {
  const notifications = useNotifications();

  // Enterprise Pattern: Advanced form hook with validation
  const form = useAdvancedForm<ChangePasswordFormData>({
    initialValues: {
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Enterprise Pattern: Simulated API call (replace with actual API)
        await new Promise((resolve) => setTimeout(resolve, 1000));
        console.log('Password changed:', { ...values, currentPassword: '***', newPassword: '***' });

        // Success notification
        notifications.showNotification({
          message: 'Password changed successfully',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' },
          close: true,
        });

        // Reset form after successful change
        form.resetForm();
      } catch (error) {
        // Error notification
        notifications.showNotification({
          message: 'Failed to change password. Please try again.',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      }
    },
  });

  return (
    <form onSubmit={form.handleSubmit()}>
      <Grid container spacing={gridSpacing}>
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            type="password"
            fullWidth
            label="Current Password"
            value={form.values.currentPassword}
            onChange={form.handleChange('currentPassword')}
            onBlur={form.handleBlur('currentPassword')}
            error={Boolean(form.touched.currentPassword && form.errors.currentPassword)}
                helperText={(form.touched.currentPassword && form.errors.currentPassword) || ''}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }} />
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            type="password"
            fullWidth
            label="New Password"
            value={form.values.newPassword}
            onChange={form.handleChange('newPassword')}
            onBlur={form.handleBlur('newPassword')}
            error={Boolean(form.touched.newPassword && form.errors.newPassword)}
            helperText={
              form.touched.newPassword
                ? form.errors.newPassword
                : 'Min 8 characters with uppercase, lowercase, number & special character'
            }
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            type="password"
            fullWidth
            label="Confirm Password"
            value={form.values.confirmPassword}
            onChange={form.handleChange('confirmPassword')}
            onBlur={form.handleBlur('confirmPassword')}
            error={Boolean(form.touched.confirmPassword && form.errors.confirmPassword)}
                helperText={(form.touched.confirmPassword && form.errors.confirmPassword) || ''}
          />
        </Grid>

        {/* Enterprise Pattern: Form status feedback */}
        {Object.keys(form.errors).length > 0 && Object.keys(form.touched).length > 0 && (
          <Grid size={{ xs: 12 }}>
            <Alert severity="error">Please fix the errors above before submitting</Alert>
          </Grid>
        )}

        <Grid size={{ xs: 12, sm: 6 }}>
          <Stack direction="row" spacing={2}>
            <AnimateButton>
              <Button
                type="submit"
                variant="outlined"
                size="large"
                disabled={form.isSubmitting || !form.isValid || !form.isDirty}
              >
                {form.isSubmitting ? 'Changing...' : 'Change Password'}
              </Button>
            </AnimateButton>
            {form.isDirty && (
              <Button variant="text" onClick={form.resetForm} disabled={form.isSubmitting}>
                Clear
              </Button>
            )}
          </Stack>
        </Grid>
      </Grid>
    </form>
  );
};

export default ChangePassword;
