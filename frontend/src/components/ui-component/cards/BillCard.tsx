import ArrowRightAltRoundedIcon from '@mui/icons-material/ArrowRightAltRounded';
import { Button, Card, CardContent, Grid, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import Link from 'next/link';

// material-ui

// types
import { GenericCardProps } from 'types';

// assets

// ==============================|| BILL CARD ||============================== //

interface BillCardProps extends GenericCardProps {
  link: string;
  bg: string;
}

const BillCard = ({ primary, secondary, link, color, bg }: BillCardProps) => {
  const theme = useTheme();
  return (
    <Card sx={{ borderLeft: '10px solid', borderColor: color, background: bg }}>
      <CardContent>
        <Grid container spacing={0}>
          <Grid size={{ xs: 12 }}>
            <Typography variant="body1" sx={{ color: theme.palette.grey[700] }}>
              {primary}
            </Typography>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Typography
              variant="h2"
              sx={{ fontWeight: 500, mb: 1.5, color: theme.palette.grey[800] }}
            >
              {secondary}
            </Typography>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Button
              variant="text"
              disableElevation
              disableRipple
              component={Link}
              href="#"
              sx={{
                color,
                p: 0,
                '&:hover': { bgcolor: 'transparent' },
              }}
              endIcon={<ArrowRightAltRoundedIcon />}
            >
              {link}
            </Button>
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );
};

export default BillCard;
