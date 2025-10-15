// material-ui
import ArrowDropDown from '@mui/icons-material/ArrowDropDown';
import Close from '@mui/icons-material/Close';
import { Alert, Autocomplete, Box, Button, Grid, InputAdornment, Stack, TextField, Typography, Chip } from '@mui/material';
import { createFilterOptions } from '@mui/material/Autocomplete';

// project imports
import { useNotifications } from 'contexts/NotificationContext';
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';
import AnimateButton from 'ui-component/extended/AnimateButton';
import { useAdvancedForm } from '@/hooks/enterprise';
import type { IValidationRule } from '@/types/common';

// ==============================|| FORM VALIDATION - AUTOCOMPLETE ENTERPRISE ||============================== //

export interface RoleProps {
  inputValue?: string;
  title: string;
  id?: number;
}

const roles: string[] = ['User', 'Admin', 'Staff', 'Manager'];
const skills = ['Java', 'HTML', 'Bootstrap', 'JavaScript', 'NodeJS', 'React', 'Angular', 'CI'];

const filter = createFilterOptions<string>();
const filterSkills = createFilterOptions<string>();

// Enterprise Pattern: Type-safe form interface
interface AutocompleteFormData {
  role: string;
  skills: string[];
}

// Enterprise Pattern: Form validation rules
const validationRules: Partial<Record<keyof AutocompleteFormData, IValidationRule[]>> = {
  role: [
    { type: 'required', message: 'Role selection is required' },
    { type: 'maxLength', value: 50, message: 'Role must be at most 50 characters' },
    {
      type: 'pattern',
      value: /^[a-z\d\-/#_\s]+$/i,
      message: 'Only alphanumerics are allowed',
    },
  ],
  skills: [
    {
      type: 'custom',
      validator: (value) => Array.isArray(value) && value.length >= 3,
      message: 'Skill tags field must have at least 3 items',
    },
    {
      type: 'custom',
      validator: (value) => Array.isArray(value) && value.length <= 15,
      message: 'Please select a maximum of 15 skills',
    },
  ],
};

const AutocompleteForms = () => {
  // Enterprise Pattern: Use modern notification system
  const notifications = useNotifications();

  // Enterprise Pattern: Advanced form hook with validation
  const form = useAdvancedForm<AutocompleteFormData>({
    initialValues: {
      role: '',
      skills: [],
    },
    validationRules,
    validateOnChange: true,
    validateOnBlur: true,
    onSubmit: async (values) => {
      try {
        // Enterprise Pattern: Simulated API call (replace with actual API)
        await new Promise((resolve) => setTimeout(resolve, 1000));
        console.log('Autocomplete form submitted:', values);

        // Success notification
        notifications.showNotification({
          message: 'Autocomplete - Submit Success',
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


  // Enterprise Pattern: Handle autocomplete changes
  const handleRoleChange = (_event: any, newValue: string) => {
    const jobExist = roles.includes(newValue);
    if (!jobExist) {
      const matchData = newValue.match(/"((?:\\.|[^"\\])*)"/);
      form.setValue('role', matchData && matchData[1] ? matchData[1] : newValue);
    } else {
      form.setValue('role', newValue);
    }
  };

  const handleSkillsChange = (_event: any, newValue: string[]) => {
    form.setValue('skills', newValue);
  };

  return (
    <MainCard title="Autocomplete">
      <form onSubmit={form.handleSubmit()}>
        <Grid container spacing={gridSpacing}>
          <Grid size={{ xs: 12 }}>
            <Autocomplete
              fullWidth
              value={form.values.role}
              disableClearable
              onChange={handleRoleChange}
              onBlur={form.handleBlur('role')}
              filterOptions={(options, params) => {
                const filtered = filter(options, params);
                const { inputValue } = params;
                const isExisting = options.some(option => inputValue === option);
                if (inputValue !== '' && !isExisting) {
                  filtered.push(`Add "${inputValue}"`);
                }
                return filtered;
              }}
              selectOnFocus
              clearOnBlur
              autoHighlight
              handleHomeEndKeys
              id="free-solo-with-text-demo"
              options={roles}
              getOptionLabel={option => {
                let value = option;
                const jobExist = roles.includes(option);
                if (!jobExist) {
                  const matchData = option.match(/"((?:\\.|[^"\\])*)"/);
                  if (matchData && matchData[1]) value = matchData && matchData[1];
                }
                return value;
              }}
              renderOption={(props, option) => {
                return (
                  <Box component="li" {...props}>
                    {option}
                  </Box>
                );
              }}
              freeSolo
              renderInput={params => (
                <TextField
                  {...(params as any)}
                  name="role"
                  error={Boolean(form.touched.role && form.errors.role)}
                  helperText={form.touched.role && form.errors.role ? form.errors.role : undefined}
                  placeholder="Select Role"
                  InputProps={{
                    ...params.InputProps,
                    sx: { bgcolor: 'grey.0' },
                    endAdornment: (
                      <InputAdornment position="end">
                        <ArrowDropDown sx={{ color: 'text.primary' }} />
                      </InputAdornment>
                    ),
                  }}
                />
              )}
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Autocomplete
              id="skills"
              multiple
              fullWidth
              autoHighlight
              freeSolo
              disableCloseOnSelect
              options={skills}
              value={form.values.skills}
              onBlur={form.handleBlur('skills')}
              getOptionLabel={option => option}
              onChange={handleSkillsChange}
              filterOptions={(options, params) => {
                const filtered = filterSkills(options, params);
                const { inputValue } = params;
                const isExisting = options.some(option => inputValue === option);
                if (inputValue !== '' && !isExisting) {
                  filtered.push(inputValue);
                }
                return filtered;
              }}
              renderOption={(props, option) => {
                return (
                  <Box component="li" {...props}>
                    {!skills.some(v => option.includes(v)) ? `Add "${option}"` : option}
                  </Box>
                );
              }}
              renderInput={params => (
                <TextField
                  {...(params as any)}
                  name="skills"
                  placeholder="Write your skills"
                  error={Boolean(form.touched.skills && form.errors.skills)}
                  helperText={form.touched.skills && form.errors.skills ? form.errors.skills : undefined}
                />
              )}
              renderTags={(value, getTagProps) =>
                value.map((option, index) => {
                  const hasError = form.touched.skills && form.errors.skills;
                  return (
                    <Chip
                      {...getTagProps({ index })}
                      key={index}
                      color={hasError ? 'error' : 'secondary'}
                      label={
                        <Typography variant="caption" color="secondary.dark">
                          {option}
                        </Typography>
                      }
                      deleteIcon={<Close />}
                      size="small"
                    />
                  );
                })
              }
            />
            <Stack
              direction="row"
              spacing={1}
              alignItems="center"
              sx={{ display: { xs: 'block', sm: 'flex' }, mt: 1.5 }}
            >
              <Typography variant="caption">Suggestion:</Typography>
              {skills
                .filter(
                  (skill: string) =>
                    form.values.skills &&
                    !form.values.skills.map(item => item).includes(skill as never)
                )
                .slice(0, 5)
                .map((option, index) => (
                  <Chip
                    sx={{ mb: { xs: '4px !important', sm: 0 } }}
                    key={index}
                    variant="outlined"
                    onClick={() =>
                      form.setValue('skills', [...form.values.skills, option])
                    }
                    label={
                      <Typography variant="caption" color="text.dark">
                        {option}
                      </Typography>
                    }
                    size="small"
                  />
                ))}
            </Stack>
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

export default AutocompleteForms;
