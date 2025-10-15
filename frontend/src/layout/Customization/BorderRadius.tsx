// material-ui
import { Grid, Slider, Stack, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import useConfig from 'hooks/useConfig';

// concat 'px'
function valueText(value: number) {
  return `${value}px`;
}

// ==============================|| BORDER - RADIUS ||============================== //

const BorderRadius = () => {
  const theme = useTheme();
  const { borderRadius, onChangeBorderRadius } = useConfig();

  return (
    <Stack spacing={2.5} pl={2} pb={2} pr={4}>
      <Typography variant="h5">BORDER RADIUS</Typography>
      <Grid container spacing={1.25} alignItems="center" justifyContent="center" size={{ xs: 12 }}>
        <Grid>
          <Typography variant="h6">4px</Typography>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <Slider
            size="small"
            value={borderRadius}
            onChange={onChangeBorderRadius}
            getAriaValueText={valueText}
            valueLabelDisplay="on"
            aria-labelledby="discrete-slider-small-steps"
            min={4}
            max={24}
            color="primary"
            sx={{
              '& .MuiSlider-valueLabel': {
                color: theme.palette.mode === 'dark' ? 'primary.dark' : 'primary.light',
              },
            }}
          />
        </Grid>
        <Grid>
          <Typography variant="h6">24px</Typography>
        </Grid>
      </Grid>
    </Stack>
  );
};

export default BorderRadius;
