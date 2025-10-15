// material-ui
import { Card, CardContent, Grid, Typography  } from '@mui/material';

// types
import { GenericCardProps } from 'types';

interface UserCountCardProps {
  primary: string;
  secondary: string;
  iconPrimary: GenericCardProps['iconPrimary'];
  color: string;
}

// =============================|| USER NUM CARD ||============================= //

const UserCountCard = ({ primary, secondary, iconPrimary, color }: UserCountCardProps) => {
  const IconPrimary = iconPrimary!;
  const primaryIcon = iconPrimary ? <IconPrimary fontSize="large" /> : null;

  return (
    <Card sx={{ background: color, position: 'relative', color: '#fff' }}>
      <CardContent>
        <Grid
          sx={{
            position: 'absolute',
            left: '-17px',
            bottom: '-27px',
            color: '#fff',
            transform: 'rotate(25deg)',
            '&> svg': {
              width: '100px',
              height: '100px',
              opacity: '0.35',
            },
          }}
        >
          {primaryIcon}
        </Grid>
        <Grid container direction="column" justifyContent="center" alignItems="center" spacing={1}>
          <Grid>
            <Typography variant="h3" align="center" color="inherit">
              {secondary}
            </Typography>
          </Grid>
          <Grid>
            <Typography variant="body1" align="center" color="inherit">
              {primary}
            </Typography>
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );
};

export default UserCountCard;
