// material-ui
import { Alert, Checkbox, FormControlLabel, Grid, Typography, TextField } from '@mui/material';

// project imports
import { useAdvancedForm } from '@/hooks/enterprise';
import { useNotifications } from 'contexts/NotificationContext';
import type { IValidationRule } from '@/types/common';

// ==============================|| FORM WIZARD - BASIC ENTERPRISE ||============================== //

// Enterprise Pattern: Type-safe form interface
interface AddressFormData {
  firstName: string;
  lastName: string;
  address1: string;
  address2: string;
  city: string;
  state: string;
  zip: string;
  country: string;
  saveAddress: boolean;
}

// Enterprise Pattern: Form validation rules
const validationRules: Partial<Record<keyof AddressFormData, IValidationRule[]>> = {
  firstName: [
    { type: 'required', message: 'First name is required' },
    { type: 'minLength', value: 2, message: 'First name must be at least 2 characters' },
  ],
  lastName: [
    { type: 'required', message: 'Last name is required' },
    { type: 'minLength', value: 2, message: 'Last name must be at least 2 characters' },
  ],
  address1: [
    { type: 'required', message: 'Address line 1 is required' },
    { type: 'minLength', value: 5, message: 'Address must be at least 5 characters' },
  ],
  city: [
    { type: 'required', message: 'City is required' },
    { type: 'minLength', value: 2, message: 'City must be at least 2 characters' },
  ],
  zip: [
    { type: 'required', message: 'Zip/Postal code is required' },
    { type: 'pattern', value: /^\d{5}(-\d{4})?$/, message: 'Invalid zip code format' },
  ],
  country: [
    { type: 'required', message: 'Country is required' },
    { type: 'minLength', value: 2, message: 'Country must be at least 2 characters' },
  ],
};

export default function AddressForm() {
  const notifications = useNotifications();

  // Enterprise Pattern: Advanced form hook with validation
  const form = useAdvancedForm<AddressFormData>({
    initialValues: {
      firstName: '',
      lastName: '',
      address1: '',
      address2: '',
      city: '',
      state: '',
      zip: '',
      country: '',
      saveAddress: false,
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Enterprise Pattern: Simulated API call (replace with actual API)
        await new Promise((resolve) => setTimeout(resolve, 1000));
        console.log('Address form submitted:', values);

        // Success notification
        notifications.showNotification({
          message: 'Address information saved successfully',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' },
          close: true,
        });
      } catch (error) {
        // Error notification
        notifications.showNotification({
          message: 'Failed to save address. Please try again.',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      }
    },
  });

  return (
    <>
      <Typography variant="h5" gutterBottom sx={{ mb: 2 }}>
        Shipping address
      </Typography>
      <form onSubmit={form.handleSubmit()}>
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              required
              id="firstNameBasic"
              name="firstName"
              label="First name"
              value={form.values.firstName}
              onChange={form.handleChange('firstName')}
              onBlur={form.handleBlur('firstName')}
              error={Boolean(form.touched.firstName && form.errors.firstName)}
              helperText={form.touched.firstName && form.errors.firstName}
              fullWidth
              autoComplete="given-name"
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              required
              id="lastNameBasic"
              name="lastName"
              label="Last name"
              value={form.values.lastName}
              onChange={form.handleChange('lastName')}
              onBlur={form.handleBlur('lastName')}
              error={Boolean(form.touched.lastName && form.errors.lastName)}
              helperText={form.touched.lastName && form.errors.lastName}
              fullWidth
              autoComplete="family-name"
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              required
              id="address1Basic"
              name="address1"
              label="Address line 1"
              value={form.values.address1}
              onChange={form.handleChange('address1')}
              onBlur={form.handleBlur('address1')}
              error={Boolean(form.touched.address1 && form.errors.address1)}
              helperText={form.touched.address1 && form.errors.address1}
              fullWidth
              autoComplete="shipping address-line1"
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              id="address2Basic"
              name="address2"
              label="Address line 2"
              value={form.values.address2}
              onChange={form.handleChange('address2')}
              onBlur={form.handleBlur('address2')}
              fullWidth
              autoComplete="shipping address-line2"
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              required
              id="cityBasic"
              name="city"
              label="City"
              value={form.values.city}
              onChange={form.handleChange('city')}
              onBlur={form.handleBlur('city')}
              error={Boolean(form.touched.city && form.errors.city)}
              helperText={form.touched.city && form.errors.city}
              fullWidth
              autoComplete="shipping address-level2"
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField 
              id="stateBasic" 
              name="state" 
              label="State/Province/Region" 
              value={form.values.state}
              onChange={form.handleChange('state')}
              onBlur={form.handleBlur('state')}
              fullWidth 
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              required
              id="zipBasic"
              name="zip"
              label="Zip / Postal code"
              value={form.values.zip}
              onChange={form.handleChange('zip')}
              onBlur={form.handleBlur('zip')}
              error={Boolean(form.touched.zip && form.errors.zip)}
              helperText={form.touched.zip && form.errors.zip}
              fullWidth
              autoComplete="shipping postal-code"
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              required
              id="countryBasic"
              name="country"
              label="Country"
              value={form.values.country}
              onChange={form.handleChange('country')}
              onBlur={form.handleBlur('country')}
              error={Boolean(form.touched.country && form.errors.country)}
              helperText={form.touched.country && form.errors.country}
              fullWidth
              autoComplete="shipping country"
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <FormControlLabel
              control={
                <Checkbox 
                  color="secondary" 
                  name="saveAddress" 
                  checked={form.values.saveAddress}
                  onChange={(event) => form.setValue('saveAddress', event.target.checked)}
                />
              }
              label="Use this address for payment details"
            />
          </Grid>

          {/* Enterprise Pattern: Form status feedback */}
          {Object.keys(form.errors).length > 0 && Object.keys(form.touched).length > 0 && (
            <Grid size={{ xs: 12 }}>
              <Alert severity="error">Please fix the errors above before submitting</Alert>
            </Grid>
          )}
        </Grid>
      </form>
    </>
  );
}
