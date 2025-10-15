// material-ui
import { Grid  } from '@mui/material';

// project imports
import BasicWizard from 'components/forms/forms-wizard/BasicWizard';
import ValidationWizard from 'components/forms/forms-wizard/ValidationWizard';
import { gridSpacing } from 'constants/index';

// ==============================|| FORMS WIZARD ||============================== //

const FormsWizard = () => (
  <Grid container spacing={gridSpacing} justifyContent="center">
    <Grid size={{ xs: 12, md: 9 }}>
      <BasicWizard />
    </Grid>
    <Grid size={{ xs: 12, md: 9 }}>
      <ValidationWizard />
    </Grid>
  </Grid>
);

export default FormsWizard;
