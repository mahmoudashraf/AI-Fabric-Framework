// material-ui
import { CardMedia, FormControlLabel, Radio, RadioGroup, Stack, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project-imports
import useConfig from 'hooks/useConfig';
import Avatar from 'ui-component/extended/Avatar';

// assets
const ltr = '/assets/images/customization/ltr.svg';
const rtl = '/assets/images/customization/rtl.svg';

// ==============================|| CUSTOMIZATION - MODE ||============================== //

const Layout = () => {
  const theme = useTheme();
  const { rtlLayout, onChangeRTL } = useConfig();

  const changeLayout = (e: any) => {
    const newRtlLayout = e.target.value === 'rtl';
    if (newRtlLayout !== rtlLayout) {
      onChangeRTL(newRtlLayout);
    }
  };
  return (
    <Stack
      direction="row"
      alignItems="center"
      pb={2}
      px={2}
      justifyContent="space-between"
      spacing={2.5}
      sx={{ width: '100%' }}
    >
      <Typography variant="h5">THEME LAYOUT</Typography>
      <RadioGroup
        row
        aria-label="layout"
        onChange={changeLayout}
        value={rtlLayout}
        name="row-radio-buttons-group"
      >
        <FormControlLabel
          control={<Radio value="ltr" sx={{ display: 'none' }} />}
          label={
            <Avatar
              size="md"
              variant="rounded"
              outline
              sx={{
                mr: 1.25,
                width: 48,
                height: 48,
                ...(rtlLayout && { borderColor: theme.palette.divider }),
              }}
            >
              <CardMedia
                component="img"
                src={ltr}
                alt="defaultLayout"
                sx={{ width: 34, height: 34 }}
              />
            </Avatar>
          }
        />
        <FormControlLabel
          control={<Radio value="rtl" sx={{ display: 'none' }} />}
          label={
            <Avatar
              size="md"
              variant="rounded"
              outline
              sx={{
                width: 48,
                height: 48,
                ...(!rtlLayout && { borderColor: theme.palette.divider }),
              }}
            >
              <CardMedia
                component="img"
                src={rtl}
                alt="defaultLayout"
                sx={{ width: 34, height: 34 }}
              />
            </Avatar>
          }
        />
      </RadioGroup>
    </Stack>
  );
};

export default Layout;
