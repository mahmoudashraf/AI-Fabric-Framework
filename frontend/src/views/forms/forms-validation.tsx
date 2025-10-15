'use client';

// material-ui
import { Grid  } from '@mui/material';

// project imports
import AutocompleteForms from 'components/forms/forms-validation/AutocompleteForms';
import CheckboxForms from 'components/forms/forms-validation/CheckboxForms';
import InstantFeedback from 'components/forms/forms-validation/InstantFeedback';
import LoginForms from 'components/forms/forms-validation/LoginForms';
import RadioGroupForms from 'components/forms/forms-validation/RadioGroupForms';
import SelectForms from 'components/forms/forms-validation/SelectForms';
import { gridSpacing } from 'constants/index';

// ==============================|| FORMS VALIDATION - FORMIK ||============================== //

const FormsValidation = () => (
  <Grid container spacing={gridSpacing}>
    <Grid size={{ xs: 12, md: 6 }}>
      <LoginForms />
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <InstantFeedback />
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <RadioGroupForms />
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <CheckboxForms />
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <SelectForms />
    </Grid>
    <Grid size={{ xs: 12, md: 6 }}>
      <AutocompleteForms />
    </Grid>
  </Grid>
);

export default FormsValidation;
