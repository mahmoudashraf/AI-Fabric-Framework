'use client';

import { Box,
  Button,
  Divider,
  Drawer,
  Fab,
  Grid,
  IconButton,
  Stack,
  Tab,
  Tabs,
  Tooltip,
  Typography,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { IconSettings, IconPlus, IconTextSize, IconColorSwatch } from '@tabler/icons-react';
import { useState } from 'react';

// material-ui

// third-party
import PerfectScrollbar from 'react-perfect-scrollbar';

// project imports
import useConfig from 'hooks/useConfig';
import MainCard from 'ui-component/cards/MainCard';
import AnimateButton from 'ui-component/extended/AnimateButton';

import BorderRadius from './BorderRadius';
import BoxContainer from './BoxContainer';
import FontFamily from './FontFamily';
import InputFilled from './InputFilled';
import Layout from './Layout';
import MenuOrientation from './MenuOrientation';
import PresetColor from './PresetColor';
import SidebarDrawer from './SidebarDrawer';
import ThemeModeLayout from './ThemeMode';

// ==============================|| LIVE CUSTOMIZATION ||============================== //

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function CustomTabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`simple-tabpanel-${index}`}
      aria-labelledby={`simple-tab-${index}`}
      {...other}
    >
      {value === index && (
        <Box>
          {children}
        </Box>
      )}
    </div>
  ) as any;
}

function a11yProps(index: number) {
  return {
    id: `simple-tab-${index}`,
    'aria-controls': `simple-tabpanel-${index}`,
  };
}

const Customization = () => {
  const theme = useTheme();
  const { onReset } = useConfig();

  // drawer on/off
  const [open, setOpen] = useState(false);
  const handleToggle = () => {
    setOpen(!open);
  };

  const [value, setValue] = useState(0);

  const handleChange = (_event: React.SyntheticEvent, newValue: number) => {
    setValue(newValue);
  };

  return (
    <>
      {/* toggle button */}
      <Tooltip title="Live Customize">
        <Fab
          component="div"
          onClick={handleToggle}
          size="medium"
          variant="circular"
          color="secondary"
          sx={{
            borderRadius: 0,
            borderTopLeftRadius: '50%',
            borderBottomLeftRadius: '50%',
            borderTopRightRadius: '50%',
            borderBottomRightRadius: '4px',
            top: '25%',
            position: 'fixed',
            right: 10,
            zIndex: 1200,
            boxShadow: theme.customShadows.secondary,
          }}
        >
          <AnimateButton type="rotate">
            <IconButton color="inherit" size="large" disableRipple aria-label="live customize">
              <IconSettings />
            </IconButton>
          </AnimateButton>
        </Fab>
      </Tooltip>

      <Drawer anchor="right" onClose={handleToggle} open={open} PaperProps={{ sx: { width: 375 } }}>
        {open && (
          <PerfectScrollbar component="div">
            <MainCard content={false} border={false}>
              <Stack
                direction="row"
                alignItems="center"
                justifyContent="space-between"
                spacing={1.5}
                sx={{ p: 2.5 }}
              >
                <Typography variant="h5">Theme Customization</Typography>
                <Stack direction="row" alignItems="center" spacing={1.25}>
                  <Button variant="outlined" color="error" size="small" onClick={() => onReset()}>
                    Reset
                  </Button>
                  <IconButton sx={{ p: 0 }} onClick={handleToggle}>
                    <IconPlus
                      size={24}
                      style={{ transform: 'rotate(45deg)', color: theme.palette.grey[600] }}
                    />
                  </IconButton>
                </Stack>
              </Stack>
              <Divider />
              <Box sx={{ width: '100%' }}>
                <Tabs
                  value={value}
                  sx={{
                    bgcolor: theme.palette.mode === 'dark' ? 'dark.800' : 'grey.50',
                    minHeight: 56,
                    '& .MuiTabs-flexContainer': { height: '100%' },
                  }}
                  centered
                  onChange={handleChange}
                  aria-label="basic tabs example"
                >
                  <Tab label={<IconColorSwatch />} {...a11yProps(0)} sx={{ width: '50%' }} />
                  <Tab label={<IconTextSize />} {...a11yProps(1)} sx={{ width: '50%' }} />
                </Tabs>
              </Box>
              <CustomTabPanel value={value} index={0}>
                <Grid container spacing={2.5}>
                  <Grid size={{ xs: 12 }}>
                    {/* layout type */}
                    <ThemeModeLayout />
                    <Divider />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    {/* Theme Preset Color */}
                    <PresetColor />
                    <Divider />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    {/* Input Background */}
                    <InputFilled />
                    <Divider />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    {/* Theme Width */}
                    <BoxContainer />
                    <Divider />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    {/* Theme Layout */}
                    <Layout />
                    <Divider />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    {/* Sidebar Drawer */}
                    <SidebarDrawer />
                    <Divider />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    {/* Menu Orientation */}
                    <MenuOrientation />
                    <Divider />
                  </Grid>
                </Grid>
              </CustomTabPanel>
              <CustomTabPanel value={value} index={1}>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12 }}>
                    {/* font family */}
                    <FontFamily />
                    <Divider />
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    {/* border radius */}
                    <BorderRadius />
                    <Divider />
                  </Grid>
                </Grid>
              </CustomTabPanel>
            </MainCard>
          </PerfectScrollbar>
        )}
      </Drawer>
    </>
  );
};

export default Customization;
