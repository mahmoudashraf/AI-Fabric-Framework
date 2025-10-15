'use client';

import { Box, Container, Grid, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import Image from 'next/image';

// material-ui

// project imports
import { gridSpacing } from 'constants/index';
import MainCard from 'ui-component/cards/MainCard';
import Accordion from 'ui-component/extended/Accordion';

// assets
const mailImg = '/assets/images/landing/widget-mail.svg';

const basicData = [
  {
    id: 'basic1',
    title: 'When do I need Extended License?',
    defaultExpand: true,
    content:
      'If your End Product which is sold - Then only your required Extended License. i.e. If you take subscription charges (monthly, yearly, etc...) from your end users in this case you required Extended License.',
  },
  {
    id: 'basic2',
    title: 'What Support Includes?',
    content:
      '6 Months of Support Includes with 1 year of free updates. We are happy to solve your bugs, issue.',
  },
  {
    id: 'basic3',
    title: 'Is Kiwi Support TypeScript?',
    content:
      'Yes, Kiwi Support the TypeScript and it is only available in Plus and Extended License.',
  },
  {
    id: 'basic4',
    title: 'Is there any RoadMap for Kiwi?',
    content:
      'Kiwi is our flagship React Dashboard Template and we always add the new features for the long run. You can check the Roadmap in Documentation.',
  },
];

// ============================|| SAAS PAGES - FAQs ||============================ //

const Faqs = () => {
  const theme = useTheme();

  return (
    <Container>
      <Grid container justifyContent="center" spacing={gridSpacing}>
        <Grid sx={{ mt: { md: 12.5, xs: 2.5 }, mb: { md: 12.5, xs: 2.5 } }}>
          <Grid container spacing={gridSpacing}>
            <Grid size={{ xs: 12 }}>
              <Typography
                variant="h1"
                color="white"
                component="div"
                sx={{
                  fontSize: '3.5rem',
                  fontWeight: 900,
                  lineHeight: 1.4,
                  [theme.breakpoints.down('md')]: { fontSize: '1.8125rem', marginTop: '80px' },
                }}
              >
                FAQs
              </Typography>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <Typography
                variant="h4"
                component="div"
                sx={{
                  fontWeight: 400,
                  lineHeight: 1.4,
                  [theme.breakpoints.up('md')]: { my: 0, mx: 12.5 },
                }}
                color="white"
              >
                Please refer the Frequently ask question for your quick help
              </Typography>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12 }} sx={{ position: 'relative', display: { xs: 'none', lg: 'block' } }}>
          <Box
            sx={{
              marginBottom: -0.625,
              position: 'absolute',
              bottom: -90,
              right: '0',
              width: 400,
              maxWidth: '100%',
              animation: '5s wings ease-in-out infinite',
            }}
          >
            <Image
              src={mailImg}
              alt="Kiwi Dashboard"
              width={400}
              height={270}
              style={{
                maxWidth: '100%',
                height: 'auto',
              }}
            />
          </Box>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <MainCard sx={{ textAlign: 'left' }} elevation={4} border={false} boxShadow shadow="4">
            <Accordion data={basicData} />
          </MainCard>
        </Grid>
      </Grid>
    </Container>
  );
};

export default Faqs;
