// material-ui
import { Alert, Button, Grid, Checkbox, FormHelperText, Stack } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { useNotifications } from 'contexts/NotificationContext';
import MainCard from 'ui-component/cards/MainCard';
import AnimateButton from 'ui-component/extended/AnimateButton';
import { useAdvancedForm } from '@/hooks/enterprise';
import type { IValidationRule } from '@/types/common';

// ==============================|| FORM VALIDATION - CHECKBOX ENTERPRISE ||============================== //

// Enterprise Pattern: Type-safe form interface
interface CheckboxFormData {
  color: string[];
}

// Enterprise Pattern: Form validation rules
const validationRules: Partial<Record<keyof CheckboxFormData, IValidationRule[]>> = {
  color: [
    {
      type: 'custom',
      validator: (value) => Array.isArray(value) && value.length > 0,
      message: 'At least one color is required',
    },
  ],
};

const CheckboxForms = () => {
  const theme = useTheme();
  
  // Enterprise Pattern: Use modern notification system
  const notifications = useNotifications();

  // Enterprise Pattern: Advanced form hook with validation
  const form = useAdvancedForm<CheckboxFormData>({
    initialValues: {
      color: [],
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Enterprise Pattern: Simulated API call (replace with actual API)
        await new Promise((resolve) => setTimeout(resolve, 1000));
        console.log('Checkbox form submitted:', values);

        // Success notification
        notifications.showNotification({
          message: 'Checkbox - Submit Success',
          variant: 'success',
          alert: { color: 'success', variant: 'filled' },
          close: true,
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

  // Enterprise Pattern: Handle checkbox change
  const handleCheckboxChange = (value: string) => {
    const currentValues = form.values.color;
    const newValues = currentValues.includes(value)
      ? currentValues.filter((v) => v !== value)
      : [...currentValues, value];
    form.setValue('color', newValues);
  };

  return (
    <MainCard title="Checkbox">
      <form onSubmit={form.handleSubmit()}>
        <Grid container spacing={2}>
          <Grid>
            <Checkbox 
              value="primary" 
              name="color" 
              color="primary" 
              checked={form.values.color.includes('primary')}
              onChange={() => handleCheckboxChange('primary')}
            />
          </Grid>
          <Grid>
            <Checkbox
              value="secondary"
              name="color"
              color="secondary"
              checked={form.values.color.includes('secondary')}
              sx={{ color: theme.palette.secondary.main }}
              onChange={() => handleCheckboxChange('secondary')}
            />
          </Grid>
          <Grid>
            <Checkbox
              value="error"
              name="color"
              checked={form.values.color.includes('error')}
              sx={{
                color: theme.palette.error.main,
                '&.Mui-checked': {
                  color: theme.palette.error.main,
                },
              }}
              onChange={() => handleCheckboxChange('error')}
            />
          </Grid>
          <Grid size={{ xs: 12 }} sx={{ pt: '0 !important' }}>
            {form.touched.color && form.errors.color && (
              <FormHelperText error id="color-helper-text">
                {form.errors.color}
              </FormHelperText>
            )}
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

export default CheckboxForms;
