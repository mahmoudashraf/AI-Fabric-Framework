// material-ui
import { CardMedia, FormControlLabel, Radio, RadioGroup, Stack, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project-imports
import useConfig from 'hooks/useConfig';
import Avatar from 'ui-component/extended/Avatar';

// assets
const mini = '/assets/images/customization/mini.svg';
const max = '/assets/images/customization/max.svg';

// ==============================|| CUSTOMIZATION - MODE ||============================== //

const SidebarDrawer = () => {
  const theme = useTheme();
  const { drawerType, onChangeDrawer } = useConfig();

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
      <Typography variant="h5">SIDEBAR DRAWER</Typography>
      <RadioGroup
        row
        aria-label="layout"
        value={drawerType}
        onChange={e => onChangeDrawer(e.target.value)}
        name="row-radio-buttons-group"
      >
        <FormControlLabel
          control={<Radio value="mini-drawer" sx={{ display: 'none' }} />}
          label={
            <Avatar
              size="md"
              variant="rounded"
              outline
              sx={{
                mr: 1.25,
                width: 48,
                height: 48,
                ...(drawerType !== 'mini-drawer' && { borderColor: theme.palette.divider }),
              }}
            >
              <CardMedia
                component="img"
                src={mini}
                alt="defaultLayout"
                sx={{ width: 34, height: 34 }}
              />
            </Avatar>
          }
        />
        <FormControlLabel
          control={<Radio value="default" sx={{ display: 'none' }} />}
          label={
            <Avatar
              size="md"
              variant="rounded"
              outline
              sx={{
                width: 48,
                height: 48,
                ...(drawerType !== 'default' && { borderColor: theme.palette.divider }),
              }}
            >
              <CardMedia
                component="img"
                src={max}
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

export default SidebarDrawer;
