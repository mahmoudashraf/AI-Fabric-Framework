// material-ui
import { Grid, Stack, Typography  } from '@mui/material';

// project imports
import { GenericCardProps } from 'types';

import MainCard from './MainCard';

// types

// ==============================|| REPORT CARD ||============================== //

interface ReportCardProps extends GenericCardProps {}

const ReportCard = ({ primary, secondary, iconPrimary, color }: ReportCardProps) => {
  const IconPrimary = iconPrimary!;
  const primaryIcon = iconPrimary ? <IconPrimary fontSize="large" /> : null;

  return (
    <MainCard>
      <Grid container justifyContent="space-between" alignItems="center">
        <Grid>
          <Stack spacing={1}>
            <Typography variant="h3">{primary}</Typography>
            <Typography variant="body1">{secondary}</Typography>
          </Stack>
        </Grid>
        <Grid>
          <Typography variant="h2" style={{ color }}>
            {primaryIcon}
          </Typography>
        </Grid>
      </Grid>
    </MainCard>
  );
};

export default ReportCard;
