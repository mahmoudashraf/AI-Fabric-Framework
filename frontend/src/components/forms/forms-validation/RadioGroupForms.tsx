// material-ui
import { Alert, Button, Grid, FormControl, FormControlLabel, FormHelperText, Radio, RadioGroup, Stack } from '@mui/material';

// project imports
import { useNotifications } from 'contexts/NotificationContext';
import MainCard from 'ui-component/cards/MainCard';
import AnimateButton from 'ui-component/extended/AnimateButton';
import { useAdvancedForm } from '@/hooks/enterprise';
import type { IValidationRule } from '@/types/common';

// ==============================|| FORM VALIDATION - RADIO GROUP ENTERPRISE ||============================== //

// Enterprise Pattern: Type-safe form interface
interface RadioGroupFormData {
  color: string;
}

// Enterprise Pattern: Form validation rules
const validationRules: Partial<Record<keyof RadioGroupFormData, IValidationRule[]>> = {
  color: [
    { type: 'required', message: 'Color selection is required' },
  ],
};

const RadioGroupForms = () => {
  // Enterprise Pattern: Use modern notification system
  const notifications = useNotifications();

  // Enterprise Pattern: Advanced form hook with validation
  const form = useAdvancedForm<RadioGroupFormData>({
    initialValues: {
      color: '',
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Enterprise Pattern: Simulated API call (replace with actual API)
        await new Promise((resolve) => setTimeout(resolve, 1000));
        console.log('Radio group form submitted:', values);

        // Success notification
        notifications.showNotification({
          message: 'Radio - Submit Success',
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
    <MainCard title="Radio">
      <form onSubmit={form.handleSubmit()}>
        <Grid container spacing={2}>
          <Grid>
            <FormControl error={Boolean(form.touched.color && form.errors.color)}>
              <RadioGroup
                row
                aria-label="color"
                value={form.values.color}
                onChange={form.handleChange('color')}
                onBlur={form.handleBlur('color')}
                name="color"
                id="color"
              >
                <FormControlLabel
                  value="primary"
                  control={
                    <Radio
                      sx={{
                        color: 'primary.main',
                        '&.Mui-checked': { color: 'primary.main' },
                      }}
                    />
                  }
                  label="Primary"
                />
                <FormControlLabel
                  value="error"
                  control={
                    <Radio
                      sx={{
                        color: 'error.main',
                        '&.Mui-checked': { color: 'error.main' },
                      }}
                    />
                  }
                  label="Error"
                />
                <FormControlLabel
                  value="secondary"
                  control={
                    <Radio
                      sx={{
                        color: 'secondary.main',
                        '&.Mui-checked': { color: 'secondary.main' },
                      }}
                    />
                  }
                  label="Secondary"
                />
              </RadioGroup>
            </FormControl>
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

export default RadioGroupForms;
