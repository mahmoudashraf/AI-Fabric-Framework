'use client';

// material-ui
import { Checkbox, FormControlLabel, Grid  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

// ==============================|| CHECKBOX ||============================== //

const UICheckbox = () => {
  const theme = useTheme();

  return (
    <MainCard
      title="Checkbox"
      secondary={<SecondaryAction link="https://next.material-ui.com/components/checkboxes/" />}
    >
      <Grid container spacing={gridSpacing}>
        <Grid size={{ xs: 12, md: 4 }}>
          <SubCard title="Basic checkboxes">
            <Grid container spacing={2}>
              <Grid>
                <Checkbox
                  color="primary"
                  sx={{
                    color: theme.palette.success.main,
                    '&.Mui-checked': { color: theme.palette.success.main },
                  }}
                />
              </Grid>
              <Grid>
                <Checkbox defaultChecked color="primary" />
              </Grid>
              <Grid>
                <Checkbox color="secondary" />
              </Grid>
              <Grid>
                <Checkbox defaultChecked color="secondary" />
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <SubCard title="With label">
            <Grid container spacing={2}>
              <Grid>
                <FormControlLabel control={<Checkbox defaultChecked />} label="Checked" />
              </Grid>
              <Grid>
                <FormControlLabel disabled control={<Checkbox />} label="Unchecked" />
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <SubCard title="Size">
            <Grid container spacing={2}>
              <Grid>
                <Checkbox defaultChecked color="primary" size="small" />
              </Grid>
              <Grid>
                <Checkbox defaultChecked color="primary" />
              </Grid>
              <Grid>
                <Checkbox
                  defaultChecked
                  color="primary"
                  sx={{ '& .MuiSvgIcon-root': { fontSize: 28 } }}
                />
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <SubCard title="With Color">
            <Grid container spacing={2}>
              <Grid>
                <Checkbox defaultChecked color="primary" />
              </Grid>
              <Grid>
                <Checkbox
                  defaultChecked
                  color="secondary"
                  sx={{ color: theme.palette.secondary.main }}
                />
              </Grid>
              <Grid>
                <Checkbox
                  defaultChecked
                  sx={{
                    color: theme.palette.error.main,
                    '&.Mui-checked': {
                      color: theme.palette.error.main,
                    },
                  }}
                />
              </Grid>
              <Grid>
                <Checkbox
                  defaultChecked
                  sx={{
                    color: theme.palette.warning.dark,
                    '&.Mui-checked': {
                      color: theme.palette.warning.main,
                    },
                  }}
                />
              </Grid>
              <Grid>
                <Checkbox
                  defaultChecked
                  sx={{
                    color: theme.palette.success.dark,
                    '&.Mui-checked': {
                      color: theme.palette.success.main,
                    },
                  }}
                />
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
      </Grid>
    </MainCard>
  );
};

export default UICheckbox;
