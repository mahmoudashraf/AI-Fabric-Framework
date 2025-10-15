// material-ui
import { Box, Drawer, Stack, useMediaQuery } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { memo, useMemo } from 'react';

// third-party
import PerfectScrollbar from 'react-perfect-scrollbar';

// project imports

import { LAYOUT_CONST } from 'constant';
import useConfig from 'hooks/useConfig';
import { drawerWidth } from 'constants/index';
import Chip from 'ui-component/extended/Chip';

// Using Context API
import { useMenuState, useMenuActions } from 'contexts/MenuContext';

import LogoSection from '../LogoSection';
import MenuList from '../MenuList';

import MenuCard from './MenuCard';
import MiniDrawerStyled from './MiniDrawerStyled';

// ==============================|| SIDEBAR DRAWER ||============================== //

const Sidebar = () => {
  const theme = useTheme();
  const matchUpMd = useMediaQuery(theme.breakpoints.up('md'));
  const matchDownMd = useMediaQuery(theme.breakpoints.down('md'));

  // Using Context API
  const { drawerOpen } = useMenuState();
  const { setDrawerOpen } = useMenuActions();

  const { layout, drawerType } = useConfig();

  const logo = useMemo(
    () => (
      <Box sx={{ display: 'flex', p: 2 }}>
        <LogoSection />
      </Box>
    ),
    []
  );

  const drawerContent = (
    <>
      <MenuList />
      {layout === LAYOUT_CONST.VERTICAL_LAYOUT && drawerOpen && <MenuCard />}
      {layout === LAYOUT_CONST.VERTICAL_LAYOUT && drawerOpen && (
        <Stack direction="row" justifyContent="center" sx={{ mb: 2 }}>
          <Chip
            label={String(process.env.REACT_APP_VERSION ?? '')}
            disabled
            chipcolor="secondary"
            size="small"
            sx={{ cursor: 'pointer' }}
          />
        </Stack>
      )}
    </>
  );

  const drawerSX = {
    paddingLeft: drawerOpen ? '16px' : 0,
    paddingRight: drawerOpen ? '16px' : 0,
    marginTop: drawerOpen ? 0 : '20px',
  };

  const drawer = useMemo(
    () => (
      <>
        {matchDownMd ? (
          <Box sx={drawerSX}>{drawerContent}</Box>
        ) : (
          <PerfectScrollbar
            component="div"
            style={{
              height: !matchUpMd ? 'calc(100vh - 56px)' : 'calc(100vh - 88px)',
              ...drawerSX,
            }}
          >
            {drawerContent}
          </PerfectScrollbar>
        )}
      </>
    ),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [matchUpMd, drawerOpen, drawerType]
  );

  return (
    <Box
      component="nav"
      sx={{ flexShrink: { md: 0 }, width: matchUpMd ? drawerWidth : 'auto' }}
      aria-label="mailbox folders"
    >
      {matchDownMd || (drawerType === LAYOUT_CONST.MINI_DRAWER && drawerOpen) ? (
        <Drawer
          variant={matchUpMd ? 'persistent' : 'temporary'}
          anchor="left"
          open={drawerOpen}
          onClose={() => setDrawerOpen(!drawerOpen)}
          sx={{
            '& .MuiDrawer-paper': {
              mt: matchDownMd ? 0 : 11,
              zIndex: 1099,
              width: drawerWidth,
              background: theme.palette.background.default,
              color: theme.palette.text.primary,
              borderRight: 'none',
            },
          }}
          ModalProps={{ keepMounted: true }}
          color="inherit"
        >
          {matchDownMd && logo}
          {drawer}
        </Drawer>
      ) : (
        <MiniDrawerStyled variant="permanent" $open={drawerOpen}>
          {logo}
          {drawer}
        </MiniDrawerStyled>
      )}
    </Box>
  );
};

export default memo(Sidebar);
