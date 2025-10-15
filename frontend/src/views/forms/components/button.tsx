'use client';

// material-ui
import LayersTwoToneIcon from '@mui/icons-material/LayersTwoTone';
import { Button, Grid, IconButton, Tooltip  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { IconSettings } from '@tabler/icons-react';

import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';
import AnimateButton from 'ui-component/extended/AnimateButton';

// assets

// ==============================|| FORMS BUTTONS ||============================== //

const UIButton = () => {
  const theme = useTheme();

  return (
    <MainCard
      title="Button"
      secondary={<SecondaryAction link="https://next.material-ui.com/components/buttons/" />}
    >
      <Grid container spacing={gridSpacing}>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Base">
            <Grid container spacing={2}>
              <Grid>
                <Button variant="contained">Primary</Button>
              </Grid>
              <Grid>
                <Button variant="contained" color="secondary">
                  Secondary
                </Button>
              </Grid>
              <Grid>
                <Button variant="contained" disabled>
                  Disabled
                </Button>
              </Grid>
              <Grid>
                <Button variant="contained" href="#contained-buttons">
                  Link
                </Button>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Colors">
            <Grid container spacing={2}>
              <Grid>
                <Button variant="contained">Primary</Button>
              </Grid>
              <Grid>
                <Button variant="contained" color="secondary">
                  Secondary
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="contained"
                  sx={{
                    background: theme.palette.success.dark,
                    '&:hover': { background: theme.palette.success.main },
                  }}
                >
                  Success
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="contained"
                  sx={{
                    background: theme.palette.error.main,
                    '&:hover': { background: theme.palette.error.dark },
                  }}
                >
                  Error
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="contained"
                  sx={{
                    background: theme.palette.warning.dark,
                    '&:hover': { background: theme.palette.warning.main },
                    color: 'grey.900',
                  }}
                >
                  warning
                </Button>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Outlined">
            <Grid container spacing={2}>
              <Grid>
                <Button variant="outlined">Primary</Button>
              </Grid>
              <Grid>
                <Button variant="outlined" color="secondary">
                  Secondary
                </Button>
              </Grid>
              <Grid>
                <Button variant="outlined" disabled>
                  Disabled
                </Button>
              </Grid>
              <Grid>
                <Button variant="outlined" href="#contained-buttons">
                  Link
                </Button>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="With Icons">
            <Grid container spacing={2}>
              <Grid>
                <Button
                  variant="contained"
                  startIcon={<LayersTwoToneIcon />}
                  aria-label="two layers"
                >
                  Button
                </Button>
              </Grid>
              <Grid>
                <Button variant="contained" endIcon={<LayersTwoToneIcon />} aria-label="two layers">
                  Button
                </Button>
              </Grid>
              <Grid>
                <Button variant="contained" aria-label="two layers">
                  <LayersTwoToneIcon />
                </Button>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Size">
            <Grid container spacing={2}>
              <Grid>
                <Button variant="contained" size="small">
                  Button
                </Button>
              </Grid>
              <Grid>
                <Button variant="contained">Button</Button>
              </Grid>
              <Grid>
                <Button variant="contained" size="large">
                  Button
                </Button>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Text Button">
            <Grid container spacing={2}>
              <Grid>
                <Button variant="text">Primary</Button>
              </Grid>
              <Grid>
                <Button variant="text" color="secondary">
                  Secondary
                </Button>
              </Grid>
              <Grid>
                <Button variant="text" disabled>
                  Disabled
                </Button>
              </Grid>
              <Grid>
                <Button variant="text" href="#contained-buttons">
                  Link
                </Button>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Shadow Button">
            <Grid container spacing={2}>
              <Grid>
                <Button
                  variant="contained"
                  sx={{
                    boxShadow: theme.customShadows.primary,
                    ':hover': {
                      boxShadow: 'none',
                    },
                  }}
                >
                  Primary
                </Button>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Animation">
            <Grid container spacing={2}>
              <Grid>
                <AnimateButton>
                  <Button
                    variant="contained"
                    sx={{
                      boxShadow: theme.customShadows.primary,
                      ':hover': {
                        boxShadow: 'none',
                      },
                    }}
                  >
                    Default
                  </Button>
                </AnimateButton>
              </Grid>
              <Grid>
                <AnimateButton
                  scale={{
                    hover: 1.1,
                    tap: 0.9,
                  }}
                >
                  <Button
                    variant="contained"
                    color="secondary"
                    sx={{
                      boxShadow: theme.customShadows.secondary,
                      ':hover': {
                        boxShadow: 'none',
                      },
                    }}
                  >
                    Scale
                  </Button>
                </AnimateButton>
              </Grid>
              <Grid>
                <AnimateButton type="slide">
                  <Button
                    variant="contained"
                    style={{ background: theme.palette.error.main, borderColor: '#EDE7F6' }}
                    sx={{
                      boxShadow: theme.customShadows.error,
                      ':hover': {
                        boxShadow: 'none',
                      },
                    }}
                  >
                    Slide
                  </Button>
                </AnimateButton>
              </Grid>
              <Grid>
                <AnimateButton type="rotate">
                  <Tooltip title="Rotate">
                    <IconButton color="primary" size="small">
                      <IconSettings stroke={1.5} size="28px" />
                    </IconButton>
                  </Tooltip>
                </AnimateButton>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
      </Grid>
    </MainCard>
  );
};

export default UIButton;
