// material-ui
import { Alert, Button, FormControl, FormHelperText, Grid, InputLabel, Select, Stack, MenuItem } from '@mui/material';

// project imports
import { useNotifications } from 'contexts/NotificationContext';
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';
import AnimateButton from 'ui-component/extended/AnimateButton';
import { useAdvancedForm } from '@/hooks/enterprise';
import type { IValidationRule } from '@/types/common';

// ==============================|| FORM VALIDATION - SELECT ENTERPRISE ||============================== //

// Enterprise Pattern: Type-safe form interface
interface SelectFormData {
  age: string | number;
}

// Enterprise Pattern: Form validation rules
const validationRules: Partial<Record<keyof SelectFormData, IValidationRule[]>> = {
  age: [
    { type: 'required', message: 'Age selection is required.' },
  ],
};

const SelectForms = () => {
  // Enterprise Pattern: Use modern notification system
  const notifications = useNotifications();

  // Enterprise Pattern: Advanced form hook with validation
  const form = useAdvancedForm<SelectFormData>({
    initialValues: {
      age: '',
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Enterprise Pattern: Simulated API call (replace with actual API)
        await new Promise((resolve) => setTimeout(resolve, 1000));
        console.log('Select form submitted:', values);

        // Success notification
        notifications.showNotification({
          message: 'Select - Submit Success',
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
    <MainCard title="Select">
      <form onSubmit={form.handleSubmit()}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <FormControl sx={{ m: 1, minWidth: 120 }} error={Boolean(form.touched.age && form.errors.age)}>
              <InputLabel id="age-select">Age</InputLabel>
              <Select
                labelId="age-select"
                id="age"
                name="age"
                value={form.values.age}
                onChange={form.handleChange('age')}
                onBlur={form.handleBlur('age')}
                label="Age"
              >
                <MenuItem value="">
                  <em>None</em>
                </MenuItem>
                <MenuItem value={10}>Ten</MenuItem>
                <MenuItem value={20}>Twenty</MenuItem>
                <MenuItem value={30}>Thirty</MenuItem>
              </Select>
              {form.touched.age && form.errors.age && (
                <FormHelperText error id="age-helper-text">
                  {form.errors.age}
                </FormHelperText>
              )}
            </FormControl>
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

export default SelectForms;
