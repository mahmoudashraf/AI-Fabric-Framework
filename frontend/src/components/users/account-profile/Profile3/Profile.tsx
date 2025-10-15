// material-ui
import { Avatar, Button, Grid, Stack, TextField, Typography, Alert } from '@mui/material';

// project imports
import useAuth from 'hooks/useAuth';
import { gridSpacing } from 'constants/index';
import SubCard from 'ui-component/cards/SubCard';
import AnimateButton from 'ui-component/extended/AnimateButton';
import { useAdvancedForm } from '@/hooks/enterprise';
import { useNotifications } from 'contexts/NotificationContext';
import type { IValidationRule } from '@/types/common';

// assets
const Avatar1 = '/assets/images/users/avatar-1.png';

// ==============================|| PROFILE 3 - PROFILE ||============================== //

// Enterprise Pattern: Type-safe form interface
interface ProfileFormData {
  name: string;
  email: string;
  company: string;
  country: string;
  phone: string;
  birthday: string;
}

// Enterprise Pattern: Form validation rules
const validationRules: Partial<Record<keyof ProfileFormData, IValidationRule[]>> = {
  name: [
    { type: 'required', message: 'Name is required' },
    { type: 'minLength', value: 2, message: 'Name must be at least 2 characters' },
  ],
  email: [
    { type: 'required', message: 'Email is required' },
    { type: 'email', message: 'Please enter a valid email address' },
  ],
  phone: [
    {
      type: 'pattern',
      value: /^\d{4}-\d{3}-\d{3}$/,
      message: 'Phone must be in format: 1234-567-890',
    },
  ],
};

const Profile = () => {
  const { user } = useAuth();
  const notifications = useNotifications();

  // Enterprise Pattern: Advanced form hook with validation
  const form = useAdvancedForm<ProfileFormData>({
    initialValues: {
      name: user?.firstName ? `${user.firstName} ${user.lastName}` : user?.email || '',
      email: 'name@example.com',
      company: 'Materially Inc.',
      country: 'USA',
      phone: '4578-420-410',
      birthday: '31/01/2001',
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
    <Grid container spacing={gridSpacing}>
      <Grid size={{ sm: 6, md: 4 }}>
        <SubCard title="Profile Picture" contentSX={{ textAlign: 'center' }}>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12 }}>
              <Avatar alt="User 1" src={Avatar1} sx={{ width: 100, height: 100, margin: '0 auto' }} />
            </Grid>
            <Grid size={{ xs: 12 }}>
              <Typography variant="subtitle2" align="center">
                Upload/Change Your Profile Image
              </Typography>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <AnimateButton>
                <Button variant="contained" size="small">
                  Upload Avatar
                </Button>
              </AnimateButton>
            </Grid>
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ sm: 6, md: 8 }}>
        <SubCard title="Edit Account Details">
          <form onSubmit={form.handleSubmit()}>
            <Grid container spacing={gridSpacing}>
              {/* Enterprise Pattern: Form with validation and error handling */}
              <Grid size={{ xs: 12 }}>
                <TextField
                  fullWidth
                  label="Name"
                  value={form.values.name}
                  onChange={form.handleChange('name')}
                  onBlur={form.handleBlur('name')}
                  error={Boolean(form.touched.name && form.errors.name)}
                  helperText={form.touched.name ? form.errors.name : 'Enter your full name'}
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField
                  fullWidth
                  label="Email address"
                  value={form.values.email}
                  onChange={form.handleChange('email')}
                  onBlur={form.handleBlur('email')}
                  error={Boolean(form.touched.email && form.errors.email)}
                  helperText={form.touched.email ? form.errors.email : undefined}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="Company"
                  value={form.values.company}
                  onChange={form.handleChange('company')}
                  onBlur={form.handleBlur('company')}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="Country"
                  value={form.values.country}
                  onChange={form.handleChange('country')}
                  onBlur={form.handleBlur('country')}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="Phone number"
                  value={form.values.phone}
                  onChange={form.handleChange('phone')}
                  onBlur={form.handleBlur('phone')}
                  error={Boolean(form.touched.phone && form.errors.phone)}
                  helperText={form.touched.phone ? form.errors.phone : 'Format: 1234-567-890'}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField
                  fullWidth
                  label="Birthday"
                  value={form.values.birthday}
                  onChange={form.handleChange('birthday')}
                  onBlur={form.handleBlur('birthday')}
                  helperText="DD/MM/YYYY"
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
                    <Button
                      type="submit"
                      variant="contained"
                      disabled={form.isSubmitting || !form.isValid || !form.isDirty}
                    >
                      {form.isSubmitting ? 'Saving...' : 'Change Details'}
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
        </SubCard>
      </Grid>
    </Grid>
  );
};

export default Profile;
