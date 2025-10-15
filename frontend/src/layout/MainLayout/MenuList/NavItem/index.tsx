import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
import {
  Avatar,
  ButtonBase,
  Chip,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Typography,
  useMediaQuery,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect } from 'react';

// material-ui

// project imports
import { LAYOUT_CONST } from 'constant';
import useConfig from 'hooks/useConfig';
// Using Context API
import { useMenuState, useMenuActions } from 'contexts/MenuContext';

// types
import { NavItemType } from 'types';

// assets

// ==============================|| SIDEBAR MENU LIST ITEMS ||============================== //

interface NavItemProps {
  item: NavItemType;
  level: number;
  parentId?: string;
  isParents?: boolean;
}

const NavItem = ({ item, level, parentId, isParents = false }: NavItemProps) => {
  const theme = useTheme();
  const matchesSM = useMediaQuery(theme.breakpoints.down('lg'));
  const matchDownMd = useMediaQuery(theme.breakpoints.down('md'));

  const pathname = usePathname();
  const { layout, borderRadius } = useConfig();

  // Using Context API
  const { selectedItem, drawerOpen } = useMenuState();
  const { setActiveItem, setActiveID, setDrawerOpen } = useMenuActions();
  const isSelected = selectedItem.findIndex(id => id === item.id) > -1;

  const Icon = item?.icon!;
  const href: string = item.url ? String(item.url) : '#';
  const itemIcon = item?.icon ? (
    <Icon
      stroke={1.5}
      size={drawerOpen ? '20px' : '24px'}
      style={{
        color: isSelected ? theme.palette.secondary.main : theme.palette.text.primary,
        ...(layout === LAYOUT_CONST.HORIZONTAL_LAYOUT &&
          isParents && { fontSize: 20, stroke: '1.5' }),
      }}
    />
  ) : (
    <FiberManualRecordIcon
      sx={{
        color: isSelected ? theme.palette.secondary.main : theme.palette.text.primary,
        width: selectedItem.findIndex(id => id === item?.id) > -1 ? 8 : 6,
        height: selectedItem.findIndex(id => id === item?.id) > -1 ? 8 : 6,
      }}
      fontSize={level > 0 ? 'inherit' : 'medium'}
    />
  );


  const itemHandler = (id: string) => {
    setActiveItem([id]);
    if (matchesSM) setDrawerOpen(false);
    setActiveID(parentId ?? null);
  };

  // active menu item on page load
  useEffect(() => {
    const currentIndex = document.location.pathname
      .toString()
      .split('/')
      .findIndex(id => id === item.id!);
    if (currentIndex > -1) {
      setActiveItem([item.id!]);
    }
    // eslint-disable-next-line
  }, [pathname]);

  const textColor = theme.palette.mode === 'dark' ? 'grey.400' : 'text.primary';
  const iconSelectedColor =
    theme.palette.mode === 'dark' && drawerOpen ? 'text.primary' : 'secondary.main';

  return (
    <>
      {layout === LAYOUT_CONST.VERTICAL_LAYOUT ||
      (layout === LAYOUT_CONST.HORIZONTAL_LAYOUT && matchDownMd) ? (
        <Link href={href} style={{ textDecoration: 'none' }}>
          <ListItemButton
            component="div"
            disabled={!!item.disabled}
            disableRipple={!drawerOpen}
            sx={{
            zIndex: 1201,
            borderRadius: `${borderRadius}px`,
            mb: 0.5,
            pl: drawerOpen ? `${level * 24}px` : 1.25,
            ...(drawerOpen &&
              level === 1 &&
              theme.palette.mode !== 'dark' && {
                '&:hover': {
                  background: theme.palette.secondary.light,
                },
                '&.Mui-selected': {
                  background: theme.palette.secondary.light,
                  color: iconSelectedColor,
                  '&:hover': {
                    color: iconSelectedColor,
                    background: theme.palette.secondary.light,
                  },
                },
              }),
            ...((!drawerOpen || level !== 1) && {
              py: level === 1 ? 0 : 1,
              '&:hover': {
                bgcolor: 'transparent',
              },
              '&.Mui-selected': {
                '&:hover': {
                  bgcolor: 'transparent',
                },
                bgcolor: 'transparent',
              },
            }),
          }}
            selected={isSelected}
            onClick={() => itemHandler(item.id!)}
          >
          <ButtonBase
            sx={{ borderRadius: `${borderRadius}px` }}
            disableRipple={drawerOpen}
            aria-label="theme-icon"
          >
            <ListItemIcon
              sx={{
                minWidth: level === 1 ? 36 : 18,
                color: isSelected ? iconSelectedColor : textColor,
                ...(!drawerOpen &&
                  level === 1 && {
                    borderRadius: `${borderRadius}px`,
                    width: 46,
                    height: 46,
                    alignItems: 'center',
                    justifyContent: 'center',
                    '&:hover': {
                      bgcolor:
                        theme.palette.mode === 'dark'
                          ? theme.palette.secondary.main + 25
                          : 'secondary.light',
                    },
                    ...(isSelected && {
                      bgcolor:
                        theme.palette.mode === 'dark'
                          ? theme.palette.secondary.main + 25
                          : 'secondary.light',
                      '&:hover': {
                        bgcolor:
                          theme.palette.mode === 'dark'
                            ? theme.palette.secondary.main + 30
                            : 'secondary.light',
                      },
                    }),
                  }),
              }}
            >
              {itemIcon}
            </ListItemIcon>
          </ButtonBase>

          {(drawerOpen || (!drawerOpen && level !== 1)) && (
            <ListItemText
              primary={
                <Typography variant={isSelected ? 'h5' : 'body1'} color="inherit">
                  {item.title}
                </Typography>
              }
              secondary={
                item.caption && (
                  <Typography
                    variant="caption"
                    sx={theme.typography.subMenuCaption as any}
                    display="block"
                    gutterBottom
                  >
                    {item.caption}
                  </Typography>
                )
              }
            />
          )}

          {drawerOpen && item.chip && (
            <Chip
              component="div"
              color={item.chip.color ?? 'default'}
              variant={item.chip.variant ?? 'filled'}
              size={item.chip.size ?? 'medium'}
              label={item.chip.label ?? ''}
              {...(item.chip.avatar ? { avatar: <Avatar>{item.chip.avatar}</Avatar> } : {})}
            />
          )}
          </ListItemButton>
        </Link>
      ) : (
        <Link href={href} style={{ textDecoration: 'none' }}>
          <ListItemButton
            component="div"
            disabled={!!item.disabled}
            {...(isParents && {
              onClick: () => {
                setActiveID(item.id!);
              },
            })}
            sx={{
            borderRadius: isParents ? `${borderRadius}px` : 0,
            mb: isParents ? 0 : 0.5,
            alignItems: 'flex-start',
            backgroundColor: level > 1 ? 'transparent !important' : 'inherit',
            py: 1,
            pl: 2,
            mr: isParents ? 1 : 0,
          }}
            selected={isSelected}
            onClick={() => itemHandler(item.id!)}
          >
          <ListItemIcon
            sx={{
              my: 'auto',
              minWidth: !item?.icon ? 18 : 36,
            }}
          >
            {itemIcon}
          </ListItemIcon>

          <ListItemText
            primary={
              <Typography variant={isSelected ? 'h5' : 'body1'} color="inherit">
                {item.title}
              </Typography>
            }
            secondary={
              item.caption && (
                <Typography
                  variant="caption"
                  sx={theme.typography.subMenuCaption as any}
                  display="block"
                  gutterBottom
                >
                  {item.caption}
                </Typography>
              )
            }
          />

          {item.chip && (
            <Chip
              component="div"
              color={item.chip.color ?? 'default'}
              variant={item.chip.variant ?? 'filled'}
              size={item.chip.size ?? 'medium'}
              label={item.chip.label ?? ''}
              {...(item.chip.avatar ? { avatar: <Avatar>{item.chip.avatar}</Avatar> } : {})}
            />
          )}
          </ListItemButton>
        </Link>
      )}
    </>
  );
};

export default NavItem;
