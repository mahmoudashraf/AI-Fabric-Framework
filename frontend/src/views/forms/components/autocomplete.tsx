'use client';

// material-ui
import EmailTwoToneIcon from '@mui/icons-material/EmailTwoTone';
import { Autocomplete, Chip, Grid, InputAdornment, TextField  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

// assets

// autocomplete options
const top100Films = [
  { label: 'The Dark Knight', id: 1 },
  { label: 'Control with Control', id: 2 },
  { label: 'Combo with Solo', id: 3 },
  { label: 'The Dark', id: 4 },
  { label: 'Fight Club', id: 5 },
  { label: 'demo@company.com', id: 6 },
  { label: 'Pulp Fiction', id: 7 },
];

// ==============================|| AUTOCOMPLETE ||============================== //

const AutoComplete = () => {
  const theme = useTheme();
  return (
    <MainCard
      title="Autocomplete"
      secondary={
        <SecondaryAction link="https://next.material-ui.com/components/autocomplete/#main-content" />
      }
    >
      <Grid container spacing={gridSpacing}>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Combo Box">
            <Grid container direction="column" spacing={3}>
              <Grid>
                <Autocomplete
                  disableClearable
                  options={top100Films}
                  defaultValue={top100Films[0]!}
                  renderInput={params => <TextField {...params} label="" size="small" InputLabelProps={{ ...(params.InputLabelProps as any), className: '' }} />}
                />
              </Grid>
              <Grid>
                <Autocomplete
                  disablePortal
                  options={top100Films}
                  defaultValue={top100Films[1] ?? undefined}
                  renderInput={params => <TextField {...params} label="" size="small" InputLabelProps={{ ...(params.InputLabelProps as any), className: '' }} />}
                />
              </Grid>
              <Grid>
                <Autocomplete
                  disablePortal
                  options={top100Films}
                  defaultValue={top100Films[2] ?? undefined}
                  renderInput={params => <TextField {...params} label="" size="small" InputLabelProps={{ ...(params.InputLabelProps as any), className: '' }} />}
                />
              </Grid>
            </Grid>
          </SubCard>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="With Caption">
            <Grid container direction="column" spacing={3}>
              <Grid>
                <Autocomplete
                  disablePortal
                  options={top100Films}
                  defaultValue={top100Films[5] ?? undefined}
                  renderInput={params => <TextField {...params} label="Email Address" size="small" InputLabelProps={{ ...(params.InputLabelProps as any), className: '' }} />}
                />
              </Grid>
              <Grid>
                <Autocomplete
                  disablePortal
                  options={top100Films}
                  defaultValue={top100Films[5] ?? undefined}
                  renderInput={params => (
                    <TextField
                      {...params}
                      label="Email Address"
                      size="small"
                      InputLabelProps={{ ...(params.InputLabelProps as any), className: '' }}
                      InputProps={{
                        ...params.InputProps,
                        startAdornment: (
                          <InputAdornment position="start">
                            <EmailTwoToneIcon
                              fontSize="small"
                              sx={{ color: theme.palette.grey[700] }}
                            />
                          </InputAdornment>
                        ),
                      }}
                    />
                  )}
                />
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Combo with Multiple Options">
            <Grid container direction="column" spacing={3}>
              <Grid>
                <Autocomplete
                  multiple
                  id="tags-outlined"
                  options={top100Films}
                  defaultValue={[top100Films[0], top100Films[4]].filter(Boolean)}
                  filterSelectedOptions
                  renderOption={(props, option) => {
                    if (!option) return null;
                    return (
                      <li {...props} key={option.id}>
                        {option.label}
                      </li>
                    );
                  }}
                  renderTags={(tagValue, getTagProps) => {
                    return tagValue.map((option, index) => {
                      if (!option) return null;
                      return (
                        <Chip {...getTagProps({ index })} key={option.id} label={option.label} />
                      );
                    });
                  }}
                  renderInput={params => <TextField {...params} size="small" InputLabelProps={{ ...(params.InputLabelProps as any), className: '' }} />}
                />
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
      </Grid>
    </MainCard>
  );
};

export default AutoComplete;
