'use client';
import { Container, AppBar, Box, CssBaseline, Toolbar, useMediaQuery } from '@mui/material';
import { styled, useTheme } from '@mui/material/styles';
import { useEffect, useMemo, FC, ReactNode } from 'react';

// material-ui

// project imports
import { LAYOUT_CONST } from 'constant';
import useConfig from 'hooks/useConfig';
import { drawerWidth } from 'constants/index';
import Breadcrumbs from 'ui-component/extended/Breadcrumbs';

// Using Context API
import { useMenuState, useMenuActions } from 'contexts/MenuContext';

import Customization from '../Customization';

import Header from './Header';
import HorizontalBar from './HorizontalBar';
import Sidebar from './Sidebar';

interface MainStyleProps {
  $open: boolean;
  $layout: string;
}

// styles
const Main = styled('main', {
  shouldForwardProp: prop => !['$open', '$layout'].includes(prop as string),
})<MainStyleProps>(({ theme, $open, $layout }) => ({
  ...theme.typography.mainContent,
  borderBottomLeftRadius: 0,
  borderBottomRightRadius: 0,
  ...(!$open && {
    transition: theme.transitions.create('margin', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.shorter + 200,
    }),
    [theme.breakpoints.up('md')]: {
      marginLeft: $layout === LAYOUT_CONST.VERTICAL_LAYOUT ? -(drawerWidth - 72) : '20px',
      width: `calc(100% - ${drawerWidth}px)`,
      marginTop: $layout === LAYOUT_CONST.HORIZONTAL_LAYOUT ? 135 : 88,
    },
  }),
  ...($open && {
    transition: theme.transitions.create('margin', {
      easing: theme.transitions.easing.easeOut,
      duration: theme.transitions.duration.shorter + 200,
    }),
    marginLeft: $layout === LAYOUT_CONST.HORIZONTAL_LAYOUT ? '20px' : 0,
    marginTop: $layout === LAYOUT_CONST.HORIZONTAL_LAYOUT ? 135 : 88,
    width: `calc(100% - ${drawerWidth}px)`,
    [theme.breakpoints.up('md')]: {
      marginTop: $layout === LAYOUT_CONST.HORIZONTAL_LAYOUT ? 135 : 88,
    },
  }),
  [theme.breakpoints.down('md')]: {
    marginLeft: '20px',
    padding: '16px',
    marginTop: 88,
    ...(!$open && {
      width: `calc(100% - ${drawerWidth}px)`,
    }),
  },
  [theme.breakpoints.down('sm')]: {
    marginLeft: '10px',
    marginRight: '10px',
    padding: '16px',
    marginTop: 88,
    ...(!$open && {
      width: `calc(100% - ${drawerWidth}px)`,
    }),
  },
}));

interface Props {
  children: ReactNode;
}

// ==============================|| MAIN LAYOUT ||============================== //

const MainLayout: FC<Props> = ({ children }) => {
  const theme = useTheme();
  const matchDownMd = useMediaQuery(theme.breakpoints.down('md'));

  // Using Context API
  const { drawerOpen } = useMenuState();
  const { setDrawerOpen } = useMenuActions();
  const { drawerType, container, layout } = useConfig();

  useEffect(() => {
    if (drawerType === LAYOUT_CONST.DEFAULT_DRAWER) {
      setDrawerOpen(true);
    } else {
      setDrawerOpen(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [drawerType]);

  useEffect(() => {
    if (drawerType === LAYOUT_CONST.DEFAULT_DRAWER) {
      setDrawerOpen(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (matchDownMd) {
      setDrawerOpen(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [matchDownMd]);

  const condition = layout === LAYOUT_CONST.HORIZONTAL_LAYOUT && !matchDownMd;

  const header = useMemo(
    () => (
      <Toolbar sx={{ p: condition ? '10px' : '16px' }}>
        <Header />
      </Toolbar>
    ),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [layout, matchDownMd]
  );

  return (
    <Box sx={{ display: 'flex' }}>
      <CssBaseline />
      {/* header */}
      <AppBar
        enableColorOnDark
        position="fixed"
        color="inherit"
        elevation={0}
        sx={{ bgcolor: theme.palette.background.default }}
      >
        {header}
      </AppBar>

      {/* horizontal menu-list bar */}
      {layout === LAYOUT_CONST.HORIZONTAL_LAYOUT && !matchDownMd && <HorizontalBar />}

      {/* drawer */}
      {(layout === LAYOUT_CONST.VERTICAL_LAYOUT || matchDownMd) && <Sidebar />}

      {/* main content */}
      <Main $open={drawerOpen} $layout={layout}>
        <Container
          maxWidth={container ? 'lg' : false}
          {...(!container && { sx: { px: { xs: 0 } } })}
        >
          {/* breadcrumb */}
          <Breadcrumbs />
          {children}
        </Container>
      </Main>
      <Customization />
    </Box>
  );
};

export default MainLayout;
