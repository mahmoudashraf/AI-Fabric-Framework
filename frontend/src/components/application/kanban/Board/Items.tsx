'use client';

import { Draggable, DraggingStyle, NotDraggingStyle } from '@hello-pangea/dnd';
import MenuBookTwoToneIcon from '@mui/icons-material/MenuBookTwoTone';
import MoreVertTwoToneIcon from '@mui/icons-material/MoreVertTwoTone';
import {
  ButtonBase,
  CardMedia,
  IconButton,
  Link,
  Menu,
  MenuItem,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import { useTheme, Theme } from '@mui/material/styles';
import { CSSProperties, useState } from 'react';

// material-ui

// third-party

// project imports
import useConfig from 'hooks/useConfig';
import { useKanban } from 'contexts/KanbanContext';
import { useNotifications } from 'contexts/NotificationContext';
// Using Context API

// Pure Context API usage
import { KanbanItem } from 'types/kanban';

import EditStory from '../Backlogs/EditStory';

import AlertItemDelete from './AlertItemDelete';

// assets

// types

interface Props {
  item: KanbanItem;
  index: number;
}

const backImage = '/assets/images/profile';

// item drag wrapper
const getDragWrapper = (
  isDragging: boolean,
  draggableStyle: DraggingStyle | NotDraggingStyle | undefined,
  theme: Theme,
  radius: string
): CSSProperties | undefined => {
  const bgcolor =
    theme.palette.mode === 'dark' ? theme.palette.background.paper + 90 : theme.palette.grey[50];
  return {
    userSelect: 'none',
    margin: `0 0 ${8}px 0`,
    padding: 16,
    border: '1px solid',
    borderColor:
      theme.palette.mode === 'dark'
        ? theme.palette.background.default
        : (theme.palette.primary[200] ?? theme.palette.primary.main) + 75,
    backgroundColor: isDragging ? bgcolor : theme.palette.background.paper,
    borderRadius: radius,
    ...draggableStyle,
  };
};

// ==============================|| KANBAN BOARD - ITEMS ||============================== //

const Items = ({ item, index }: Props) => {
  const theme = useTheme();
  
  // Using Context API
  const kanbanContext = useKanban();
  const notificationContext = useNotifications();
  
  const backProfile = item.image ? `${backImage}/${item.image}` : undefined;

  const { borderRadius } = useConfig();
  const kanban = kanbanContext.state;
  const { userStory, items, columns } = kanban;

  const itemStory = userStory.filter(
    story => story?.itemIds?.filter(itemId => itemId === item?.id)[0]
  )[0];

  const handlerDetails = (id: string) => {
    try {
      kanbanContext.selectItem(id);
    } catch (error) {
      notificationContext.showNotification({
        message: 'Failed to select item',
        variant: 'error',
        alert: {
          color: 'error',
          variant: 'filled',
        },
        close: true,
      });
    }
  };

  const [anchorEl, setAnchorEl] = useState<Element | (() => Element) | null | undefined>(null);
  const handleClick = (event: React.MouseEvent<HTMLButtonElement> | undefined) => {
    setAnchorEl(event?.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const [open, setOpen] = useState(false);
  const handleModalClose = (status: boolean) => {
    setOpen(false);
    if (status) {
      try {
        kanbanContext.deleteItem(item.id, items, columns, userStory);
        notificationContext.showNotification({
          message: 'Task Delete successfully',
          variant: 'success',
          alert: {
            color: 'success',
            variant: 'filled',
          },
          close: true,
        });
      } catch (error) {
        notificationContext.showNotification({
          message: 'Failed to delete task',
          variant: 'error',
          alert: {
            color: 'error',
            variant: 'filled',
          },
          close: true,
        });
      }
    }
  };

  const [openStoryDrawer, setOpenStoryDrawer] = useState<boolean>(false);
  const handleStoryDrawerOpen = () => {
    setOpenStoryDrawer(prevState => !prevState);
  };

  const editStory = () => {
    setOpenStoryDrawer(prevState => !prevState);
  };

  return (
    <>
      {item && (
        <Draggable key={item.id} draggableId={item.id} index={index}>
          {(provided, snapshot) => (
            <div
              ref={provided.innerRef}
              {...provided.draggableProps}
              {...provided.dragHandleProps}
              style={getDragWrapper(
                snapshot.isDragging,
                provided.draggableProps.style,
                theme,
                `${borderRadius}px`
              )}
            >
              <Stack
                direction="row"
                justifyContent="space-between"
                alignItems="center"
                sx={{ mb: itemStory ? -0.75 : 0 }}
              >
                <Typography
                  onClick={() => handlerDetails(item.id)}
                  variant="subtitle1"
                  sx={{
                    display: 'inline-block',
                    width: 'calc(100% - 34px)',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    verticalAlign: 'middle',
                    cursor: 'pointer',
                  }}
                >
                  {item.title}
                </Typography>

                <ButtonBase
                  sx={{ borderRadius: '12px' }}
                  onClick={handleClick}
                  aria-controls="menu-comment"
                  aria-haspopup="true"
                >
                  <IconButton component="span" size="small" disableRipple aria-label="more options">
                    <MoreVertTwoToneIcon fontSize="inherit" />
                  </IconButton>
                </ButtonBase>
                <Menu
                  id="menu-comment"
                  anchorEl={anchorEl}
                  keepMounted
                  open={Boolean(anchorEl)}
                  onClose={handleClose}
                  variant="selectedMenu"
                  anchorOrigin={{
                    vertical: 'bottom',
                    horizontal: 'right',
                  }}
                  transformOrigin={{
                    vertical: 'top',
                    horizontal: 'right',
                  }}
                >
                  <MenuItem
                    onClick={() => {
                      handleClose();
                      handlerDetails(item.id);
                    }}
                  >
                    Edit
                  </MenuItem>
                  <MenuItem
                    onClick={() => {
                      handleClose();
                      setOpen(true);
                    }}
                  >
                    Delete
                  </MenuItem>
                </Menu>
                {open && (
                  <AlertItemDelete title={item.title} open={open} handleClose={handleModalClose} />
                )}
              </Stack>
              {itemStory && (
                <>
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <Tooltip title="User Story">
                      <MenuBookTwoToneIcon color="secondary" sx={{ fontSize: '0.875rem' }} />
                    </Tooltip>
                    <Tooltip title={itemStory.title}>
                      <Link
                        variant="caption"
                        color="secondary"
                        underline="hover"
                        onClick={editStory}
                        sx={{ cursor: 'pointer', pt: 0.5 }}
                      >
                        User Story #{itemStory.id}
                      </Link>
                    </Tooltip>
                  </Stack>
                  {openStoryDrawer && (
                    <EditStory
                      story={itemStory}
                      open={openStoryDrawer}
                      handleDrawerOpen={handleStoryDrawerOpen}
                    />
                  )}
                </>
              )}
              {backProfile && (
                <CardMedia
                  component="img"
                  image={backProfile}
                  sx={{ width: '100%', borderRadius: 1, mt: 1.5 }}
                  title="Slider5 image"
                />
              )}
            </div>
          )}
        </Draggable>
      )}
    </>
  );
};

export default Items;
