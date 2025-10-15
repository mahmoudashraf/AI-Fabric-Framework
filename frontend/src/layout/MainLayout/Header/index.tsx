// material-ui
import { Avatar, Box, useMediaQuery } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { IconMenu2 } from '@tabler/icons-react';

import { LAYOUT_CONST } from 'constant';
import useConfig from 'hooks/useConfig';
// Using Context API
import { useMenuState, useMenuActions } from 'contexts/MenuContext';

import LogoSection from '../LogoSection';

import FullScreenSection from './FullScreenSection';
import LocalizationSection from './LocalizationSection';
import MegaMenuSection from './MegaMenuSection';
import MobileSection from './MobileSection';
import NotificationSection from './NotificationSection';
import ProfileSection from './ProfileSection';
import SearchSection from './SearchSection';

// assets

// ==============================|| MAIN NAVBAR / HEADER ||============================== //

const Header = () => {
  const theme = useTheme();

  // Using Context API
  const { drawerOpen } = useMenuState();
  const { setDrawerOpen } = useMenuActions();

  const matchDownMd = useMediaQuery(theme.breakpoints.down('md'));
  const { layout } = useConfig();

  return (
    <>
      {/* logo & toggler button */}
      <Box
        sx={{
          width: 228,
          display: 'flex',
          [theme.breakpoints.down('md')]: {
            width: 'auto',
          },
        }}
      >
        <Box component="span" sx={{ display: { xs: 'none', md: 'block' }, flexGrow: 1 }}>
          <LogoSection />
        </Box>
        {(layout === LAYOUT_CONST.VERTICAL_LAYOUT ||
          (layout === LAYOUT_CONST.HORIZONTAL_LAYOUT && matchDownMd)) && (
          <Avatar
            component="div"
            variant="rounded"
            sx={{
              ...theme.typography.commonAvatar,
              ...theme.typography.mediumAvatar,
              overflow: 'hidden',
              transition: 'all .2s ease-in-out',
              background:
                theme.palette.mode === 'dark'
                  ? theme.palette.dark.main
                  : theme.palette.secondary.light,
              color:
                theme.palette.mode === 'dark'
                  ? theme.palette.secondary.main
                  : theme.palette.secondary.dark,
              '&:hover': {
                background:
                  theme.palette.mode === 'dark'
                    ? theme.palette.secondary.main
                    : theme.palette.secondary.dark,
                color:
                  theme.palette.mode === 'dark'
                    ? theme.palette.secondary.light
                    : theme.palette.secondary.light,
              },
            } as any}
            onClick={() => setDrawerOpen(!drawerOpen)}
            color="inherit"
          >
            <IconMenu2 stroke={1.5} size="20px" />
          </Avatar>
        )}
      </Box>

      {/* header search */}
      <SearchSection />
      <Box sx={{ flexGrow: 1 }} />
      <Box sx={{ flexGrow: 1 }} />

      {/* mega-menu */}
      <Box sx={{ display: { xs: 'none', sm: 'block' } }}>
        <MegaMenuSection />
      </Box>

      {/* live customization & localization */}
      <Box sx={{ display: { xs: 'none', sm: 'block' } }}>
        <LocalizationSection />
      </Box>

      {/* notification */}
      <NotificationSection />

      {/* full sceen toggler */}
      <Box sx={{ display: { xs: 'none', lg: 'block' } }}>
        <FullScreenSection />
      </Box>

      {/* profile */}
      <ProfileSection />

      {/* mobile header */}
      <Box sx={{ display: { xs: 'block', sm: 'none' } }}>
        <MobileSection />
      </Box>
    </>
  );
};

export default Header;
