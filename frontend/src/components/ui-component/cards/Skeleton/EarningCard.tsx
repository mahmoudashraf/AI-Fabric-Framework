// material-ui
import { Card, CardContent, Grid  } from '@mui/material';
import Skeleton from '@mui/material/Skeleton';

// ==============================|| SKELETON - EARNING CARD ||============================== //

const EarningCard = () => (
  <Card>
    <CardContent>
      <Grid container direction="column">
        <Grid>
          <Grid container justifyContent="space-between">
            <Grid>
              <Skeleton variant="rectangular" width={44} height={44} />
            </Grid>
            <Grid>
              <Skeleton variant="rectangular" width={34} height={34} />
            </Grid>
          </Grid>
        </Grid>
        <Grid>
          <Skeleton variant="rectangular" sx={{ my: 2 }} height={40} />
        </Grid>
        <Grid>
          <Skeleton variant="rectangular" height={30} />
        </Grid>
      </Grid>
    </CardContent>
  </Card>
);

export default EarningCard;
