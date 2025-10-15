'use client';

// material-ui
import { Grid  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { IconTallymark1 } from '@tabler/icons-react';

import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';
import Breadcrumb from 'ui-component/extended/Breadcrumbs';

// assets

// =============================|| UI BREADCRUMB ||============================= //

const UIBreadcrumb = () => {
  const theme = useTheme();

  return (
    <MainCard
      title="Breadcrumb"
      secondary={<SecondaryAction link="https://next.material-ui.com/components/breadcrumbs/" />}
    >
      <Grid container spacing={gridSpacing}>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Basic">
            <Breadcrumb
              sx={{
                mb: '0px !important',
                bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50',
              }}
            />
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Custom Separator">
            <Breadcrumb
              separator={IconTallymark1}
              sx={{
                mb: '0px !important',
                bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50',
              }}
            />
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="With Title">
            <Breadcrumb
              sx={{
                mb: '0px !important',
                bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50',
              }}
            />
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Title Right">
            <Breadcrumb
              titleBottom
              sx={{
                mb: '0px !important',
                bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50',
              }}
            />
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Title Top">
            <Breadcrumb
              rightAlign={false}
              sx={{
                mb: '0px !important',
                bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50',
              }}
            />
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Title Bottom">
            <Breadcrumb
              rightAlign={false}
              titleBottom
              sx={{
                mb: '0px !important',
                bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50',
              }}
            />
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="With Icons">
            <Breadcrumb
              icons
              sx={{
                mb: '0px !important',
                bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50',
              }}
            />
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Only Dashboard Icons">
            <Breadcrumb
              icon
              sx={{
                mb: '0px !important',
                bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50',
              }}
            />
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Collapsed Breadcrumbs">
            <Breadcrumb
              title
              maxItems={2}
              sx={{
                mb: '0px !important',
                bgcolor: theme.palette.mode === 'dark' ? 'dark.main' : 'grey.50',
              }}
            />
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="No Card with Divider">
            <Breadcrumb card={false} divider sx={{ mb: '0px !important' }} />
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="No Card & No Divider">
            <Breadcrumb card={false} sx={{ mb: '0px !important' }} />
          </SubCard>
        </Grid>
      </Grid>
    </MainCard>
  );
};

export default UIBreadcrumb;
