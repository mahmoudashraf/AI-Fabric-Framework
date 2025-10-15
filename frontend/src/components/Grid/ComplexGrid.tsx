import { ButtonBase, Typography, Grid } from '@mui/material';
import { styled } from '@mui/material/styles';
import Image from 'next/image';

// material-ui

// project imports
import SubCard from 'ui-component/cards/SubCard';

// assets
const banner = '/assets/images/profile/profile-back-10.png';

// styled constant
const Img = styled(Image)({
  margin: 'auto',
  display: 'block',
  maxWidth: '100%',
  maxHeight: '100%',
});

// ===============================|| GRID - COMPLEX ||=============================== //

export default function ComplexGrid() {
  return (
    <SubCard content={false} sx={{ p: 2, margin: 'auto', maxWidth: 500, flexGrow: 1 }}>
      <Grid container spacing={2}>
        <Grid>
          <ButtonBase sx={{ width: { xs: '100%', sm: 200 }, height: '100%' }}>
            <Img alt="complex" src={banner} fill sizes="100vw" />
          </ButtonBase>
        </Grid>
        <Grid size={{ xs: 12, sm: 'grow' }} container>
          <Grid size="grow" container direction="column" spacing={2}>
            <Grid size="grow">
              <Typography gutterBottom variant="subtitle1" component="div">
                Standard license
              </Typography>
              <Typography variant="body2" gutterBottom>
                Full resolution 1920x1080 • JPEG
              </Typography>
              <Typography variant="body2" color="text.secondary">
                ID: 1030114
              </Typography>
            </Grid>
            <Grid>
              <Typography sx={{ cursor: 'pointer' }} variant="body2" color="error">
                Remove
              </Typography>
            </Grid>
          </Grid>
          <Grid>
            <Typography variant="subtitle1" component="div">
              $19.00
            </Typography>
          </Grid>
        </Grid>
      </Grid>
    </SubCard>
  );
}
