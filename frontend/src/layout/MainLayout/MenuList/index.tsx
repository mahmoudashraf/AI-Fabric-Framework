// material-ui
import { Box, Divider, List, Typography, useMediaQuery } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { memo } from 'react';

// project imports
import { HORIZONTAL_MAX_ITEM } from 'config';
import { LAYOUT_CONST } from 'constant';
import useConfig from 'hooks/useConfig';
import menuItem from 'menu-items';
import { useMenuState } from 'contexts/MenuContext';

// types
import { NavItemType } from 'types';

import NavGroup from './NavGroup';
import NavItem from './NavItem';

// ==============================|| SIDEBAR MENU LIST ||============================== //

const MenuList = () => {
  const theme = useTheme();
  const { layout } = useConfig();
  const { drawerOpen } = useMenuState();

  const matchDownMd = useMediaQuery(theme.breakpoints.down('md'));

  // last menu-item to show in horizontal menu bar
  const lastItem =
    layout === LAYOUT_CONST.HORIZONTAL_LAYOUT && !matchDownMd ? HORIZONTAL_MAX_ITEM : undefined;

  let lastItemIndex = menuItem.items.length - 1;
  let remItems: NavItemType[] = [];
  let lastItemId: string = '';

  if (typeof lastItem === 'number' && lastItem < menuItem.items.length) {
    const targetItem = menuItem.items[lastItem - 1];
    lastItemId = targetItem?.id || '';
    lastItemIndex = lastItem - 1;
    remItems = menuItem.items.slice(lastItem - 1, menuItem.items.length).map(item => ({
      title: item.title,
      elements: item.children ?? [],
      icon: item.icon,
      ...(item.url && {
        url: item.url,
      }),
    }));
  }

  const navItems = menuItem.items.slice(0, lastItemIndex + 1).map(item => {
    switch (item.type) {
      case 'group':
        if (item.url && item.id !== lastItemId) {
          return (
            <List key={item.id}>
              <NavItem item={item} level={1} isParents />
              {layout !== LAYOUT_CONST.HORIZONTAL_LAYOUT && <Divider sx={{ py: 0.5 }} />}
            </List>
          );
        }
        const navGroupProps: {
          item: NavItemType;
          lastItem?: number;
          remItems: NavItemType[];
          lastItemId: string;
        } = {
          item: item,
          ...(lastItem !== undefined && { lastItem }),
          remItems: remItems,
          lastItemId: lastItemId || '',
        };
        
        return <NavGroup key={item.id || `nav-group-${item.title || 'unknown'}`} {...navGroupProps} />;
      default:
        return (
          <Typography key={item.id} variant="h6" color="error" align="center">
            Menu Items Error
          </Typography>
        );
    }
  });

  return layout === LAYOUT_CONST.VERTICAL_LAYOUT ||
    (layout === LAYOUT_CONST.HORIZONTAL_LAYOUT && matchDownMd) ? (
    <Box {...(drawerOpen && { sx: { mt: 1.5 } })}>{navItems}</Box>
  ) : (
    <>{navItems}</>
  );
};

export default memo(MenuList);
