// material-ui
import { Alert, Button, Grid, Stack, TextField, Typography } from '@mui/material';

// project imports
import { gridSpacing } from 'constants/index';
import Avatar from 'ui-component/extended/Avatar';
import AnimateButton from 'ui-component/extended/AnimateButton';
import { useAdvancedForm } from '@/hooks/enterprise';
import { useNotifications } from 'contexts/NotificationContext';
import type { IValidationRule } from '@/types/common';

// assets
const Avatar1 = '/assets/images/users/avatar-1.png';
import ErrorTwoToneIcon from '@mui/icons-material/ErrorTwoTone';

// ==============================|| PROFILE 2 - USER PROFILE ||============================== //

// Enterprise Pattern: Type-safe form interface
interface UserProfileFormData {
  lastName: string;
  firstName: string;
  email: string;
  phone: string;
  company: string;
  website: string;
}

// Enterprise Pattern: Form validation rules
const validationRules: Partial<Record<keyof UserProfileFormData, IValidationRule[]>> = {
  lastName: [
    { type: 'required', message: 'Last name is required' },
    { type: 'minLength', value: 2, message: 'Last name must be at least 2 characters' },
  ],
  firstName: [
    { type: 'required', message: 'First name is required' },
    { type: 'minLength', value: 2, message: 'First name must be at least 2 characters' },
  ],
  email: [
    { type: 'required', message: 'Email is required' },
    { type: 'email', message: 'Please enter a valid email address' },
  ],
  phone: [
    {
      type: 'pattern',
      value: /^\d{3}-\d{2}-\d{5}$/,
      message: 'Phone must be in format: 000-00-00000',
    },
  ],
};

const UserProfile = () => {
  const notifications = useNotifications();

  // Enterprise Pattern: Advanced form hook with validation
  const form = useAdvancedForm<UserProfileFormData>({
    initialValues: {
      lastName: 'Schorl',
      firstName: 'Delaney',
      email: 'demo@company.com',
      phone: '000-00-00000',
      company: 'company.ltd',
      website: 'www.company.com',
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Enterprise Pattern: Simulated API call (replace with actual API)
        await new Promise((resolve) => setTimeout(resolve, 1000));
        console.log('Profile updated:', values);

        // Success notification
        notifications.showNotification({
          message: 'Profile updated successfully',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' },
          close: true,
        });
      } catch (error) {
        // Error notification
        notifications.showNotification({
          message: 'Failed to update profile. Please try again.',
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
        <Grid size={{ xs: 12 }}>
          <Grid container spacing={2} alignItems="center">
            <Grid>
              <Avatar alt="User 1" src={Avatar1} sx={{ height: 80, width: 80 }} />
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }}>
              <Grid container spacing={1}>
                <Grid size={{ xs: 12 }}>
                  <Stack direction="row" spacing={2} alignItems="center">
                    <input accept="image/*" style={{ display: 'none' }} id="contained-button-file" multiple type="file" />
                  </Stack>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="caption">
                    <ErrorTwoToneIcon sx={{ height: 16, width: 16, mr: 1, verticalAlign: 'text-bottom' }} />
                    Image size Limit should be 125kb Max.
                  </Typography>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Grid>

        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="Last Name"
            value={form.values.lastName}
            onChange={form.handleChange('lastName')}
            onBlur={form.handleBlur('lastName')}
            error={Boolean(form.touched.lastName && form.errors.lastName)}
                helperText={(form.touched.lastName && form.errors.lastName) || ''}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="First Name"
            value={form.values.firstName}
            onChange={form.handleChange('firstName')}
            onBlur={form.handleBlur('firstName')}
            error={Boolean(form.touched.firstName && form.errors.firstName)}
                helperText={(form.touched.firstName && form.errors.firstName) || ''}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="Email Address"
            value={form.values.email}
            onChange={form.handleChange('email')}
            onBlur={form.handleBlur('email')}
            error={Boolean(form.touched.email && form.errors.email)}
                helperText={(form.touched.email && form.errors.email) || ''}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="Phone Number"
            value={form.values.phone}
            onChange={form.handleChange('phone')}
            onBlur={form.handleBlur('phone')}
            error={Boolean(form.touched.phone && form.errors.phone)}
            helperText={form.touched.phone ? form.errors.phone : 'Format: 000-00-00000'}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="Company Name"
            value={form.values.company}
            onChange={form.handleChange('company')}
            onBlur={form.handleBlur('company')}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="Site Information"
            value={form.values.website}
            onChange={form.handleChange('website')}
            onBlur={form.handleBlur('website')}
          />
        </Grid>

        {/* Enterprise Pattern: Form status feedback */}
        {Object.keys(form.errors).length > 0 && Object.keys(form.touched).length > 0 && (
          <Grid size={{ xs: 12 }}>
            <Alert severity="error">Please fix the errors above before submitting</Alert>
          </Grid>
        )}

        <Grid size={{ xs: 12 }}>
          <Stack direction="row" spacing={2}>
            <AnimateButton>
              <Button type="submit" variant="contained" disabled={form.isSubmitting || !form.isValid || !form.isDirty}>
                {form.isSubmitting ? 'Saving...' : 'Save Changes'}
              </Button>
            </AnimateButton>
            {form.isDirty && (
              <Button variant="outlined" onClick={form.resetForm} disabled={form.isSubmitting}>
                Reset
              </Button>
            )}
          </Stack>
        </Grid>
      </Grid>
    </form>
  );
};

export default UserProfile;
