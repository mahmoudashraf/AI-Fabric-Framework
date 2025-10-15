// material-ui
import { Grid  } from '@mui/material';

// project import
import { gridSpacing } from 'constants/index';

import AutoStop from './AutoStop';
import ConfirmationSave from './ConfirmationSave';
import Controlled from './Controlled';
import CustomEdit from './CustomEdit';
import DisableEditing from './DisableEditing';
import EditableColumn from './EditableColumn';
import EditableRow from './EditableRow';
import EditingEvents from './EditingEvents';
import FullFeaturedCrudGrid from './FullFeatured';
import ParserSetter from './ParserSetter';
import ServerValidation from './ServerValidation';
import Validation from './Validation';

// ==============================|| INLINE EDITING DATA GRID ||============================== //

export default function InlineEditing() {
  return (
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12 }}>
        <FullFeaturedCrudGrid />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <EditableColumn />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <EditableRow />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <EditingEvents />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <DisableEditing />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <ServerValidation />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <ConfirmationSave />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <ParserSetter />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Validation />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Controlled />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <CustomEdit />
      </Grid>
      <Grid size={{ xs: 12 }}>
        <AutoStop />
      </Grid>
    </Grid>
  );
}
