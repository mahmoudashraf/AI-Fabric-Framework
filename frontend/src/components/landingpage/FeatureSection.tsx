// material-ui
import { Container, Grid, Typography, Stack, CardMedia  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import SubCard from 'ui-component/cards/SubCard';
import Avatar from 'ui-component/extended/Avatar';

import FadeInWhenVisible from './Animation';

// assets
const Offer1 = '/assets/images/landing/offer/offer-1.png';
const Offer2 = '/assets/images/landing/offer/offer-2.png';
const Offer3 = '/assets/images/landing/offer/offer-3.png';
const Offer4 = '/assets/images/landing/offer/offer-4.png';
const Offer5 = '/assets/images/landing/offer/offer-5.png';
const Offer6 = '/assets/images/landing/offer/offer-6.png';

// =============================|| LANDING - FEATURE PAGE ||============================= //

interface OfferCardProps {
  title: string;
  caption: string;
  image: string;
}
const OfferCard = ({ title, caption, image }: OfferCardProps) => {
  const theme = useTheme();
  const AvaterSx = {
    background: 'transparent',
    color: theme.palette.secondary.main,
    width: 56,
    height: 56,
  };
  return (
    <FadeInWhenVisible>
      <SubCard
        sx={{
          bgcolor: theme.palette.mode === 'dark' ? 'dark.800' : 'grey.100',
          borderColor:
            theme.palette.mode === 'dark'
              ? theme.palette.background.default
              : theme.palette.divider,
          '&:hover': { boxShadow: 'none' },
          height: '100%',
        }}
      >
        <Stack spacing={4}>
          <Avatar variant="rounded" sx={AvaterSx}>
            <CardMedia component="img" src={image} alt="Beautiful User Interface" />
          </Avatar>
          <Stack spacing={2}>
            <Typography variant="h3" sx={{ fontWeight: 500 }}>
              {title}
            </Typography>
            <Typography variant="body2" sx={{ fontSize: '1rem' }}>
              {caption}
            </Typography>
          </Stack>
        </Stack>
      </SubCard>
    </FadeInWhenVisible>
  );
};

const FeatureSection = () => (
  <Container>
    <Grid container spacing={7.5} justifyContent="center">
      <Grid size={{ xs: 12, md: 6 }} sx={{ textAlign: 'center' }}>
        <Grid container spacing={1.5}>
          <Grid size={{ xs: 12 }}>
            <Typography variant="h2" sx={{ fontSize: { xs: '1.5rem', sm: '2.125rem' } }}>
              What does Kiwi offer?
            </Typography>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Typography variant="body2" sx={{ fontSize: '1rem' }}>
              Kiwi is a reliable choice for your admin panel needs, offering a wide range of
              features to easily manage your backend panel
            </Typography>
          </Grid>
        </Grid>
      </Grid>
      <Grid size={{ xs: 12 }}>
        <Grid
          container
          justifyContent="center"
          spacing={5}
          sx={{ '&> .MuiGrid-root > div': { height: '100%' } }}
        >
          <Grid>
            <OfferCard
              title="Beautiful User Interface"
              caption="Kiwi can improve the user experience of your web application by providing a clear and intuitive layout, and consistent look and feel."
              image={Offer1}
            />
          </Grid>
          <Grid>
            <OfferCard
              title="Time and Cost Savings"
              caption="Kiwi can save developers time and effort by providing a pre-built user interface, allowing them to focus on other aspects of the project."
              image={Offer2}
            />
          </Grid>
          <Grid>
            <OfferCard
              title="Reduce Development Complexity"
              caption="Kiwi simplifies admin panel development with easy theme setup and clear code with flexible layouts options."
              image={Offer3}
            />
          </Grid>
          <Grid>
            <OfferCard
              title="Improved Scalability"
              caption="Kiwi uses scalable technologies and resources to ensure that your admin panel remains efficient and effective as your needs evolve."
              image={Offer4}
            />
          </Grid>
          <Grid>
            <OfferCard
              title=" Well-Documented and Supported"
              caption="With a range of resources including user guides, tutorials, and FAQs to help users understand and effectively use the Kiwi."
              image={Offer5}
            />
          </Grid>
          <Grid>
            <OfferCard
              title="Performance Centric"
              caption="Kiwi is a performance-centric dashboard template that is designed to deliver optimal performance for your admin panel."
              image={Offer6}
            />
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  </Container>
);

export default FeatureSection;
