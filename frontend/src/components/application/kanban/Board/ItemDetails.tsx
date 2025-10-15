'use client';

import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import HighlightOffIcon from '@mui/icons-material/HighlightOff';
import { Box, Button, Divider, Drawer, Grid, Stack, Typography  } from '@mui/material';
import { useEffect, useState, ReactElement } from 'react';

// third party
import PerfectScrollbar from 'react-perfect-scrollbar';

// project imports
import { useKanban } from 'contexts/KanbanContext';
import { useNotifications } from 'contexts/NotificationContext';

// types
import { KanbanItem } from 'types/kanban';

import AddItemComment from './AddItemComment';
import AlertItemDelete from './AlertItemDelete';
import EditItem from './EditItem';
import ItemComment from './ItemComment';

// ==============================|| KANBAN BOARD - ITEM DETAILS ||============================== //

const ItemDetails = () => {
  let selectedData: KanbanItem;
  let commentList: ReactElement | ReactElement[] = <></>;

  const kanbanContext = useKanban();
  const notificationContext = useNotifications();
  const kanban = kanbanContext.state;
  const { columns, comments, profiles, items, selectedItem, userStory } = kanban;

  // drawer
  const [open, setOpen] = useState<boolean>(selectedItem !== false);
  const handleDrawerOpen = () => {
    setOpen(prevState => !prevState);
    kanbanContext.selectItem(false);
  };

  useEffect(() => {
    if (selectedItem !== false) setOpen(true);
  }, [selectedItem]);

  if (selectedItem !== false) {
    const foundItem = items.find(item => item.id === selectedItem);
    if (foundItem) {
      selectedData = foundItem;
      if (selectedData?.commentIds) {
        commentList = [...selectedData.commentIds].reverse().map((commentId, index) => {
          const commentData = comments.find(comment => comment.id === commentId);
          if (!commentData) return <></>;
          const profile = profiles.find(item => item.id === commentData.profileId);
          if (!profile) return <></>;
          return <ItemComment key={index} comment={commentData} profile={profile} />;
        });
      }
    }
  }

  const [openModal, setOpenModal] = useState(false);

  const handleModalClose = (status: boolean) => {
    setOpenModal(false);
    if (status) {
      handleDrawerOpen();
      kanbanContext.deleteItem(selectedData.id, items, columns, userStory);
      notificationContext.showNotification({
        message: 'Task Deleted successfully',
        variant: 'success',
        alert: {
          color: 'success',
          variant: 'filled',
        },
        close: true,
      });
    }
  };

  return (
    <Drawer
      sx={{
        ml: open ? 3 : 0,
        flexShrink: 0,
        zIndex: 1200,
        overflowX: 'hidden',
        width: { xs: 320, md: 450 },
        '& .MuiDrawer-paper': {
          height: '100vh',
          width: { xs: 320, md: 450 },
          position: 'fixed',
          border: 'none',
          borderRadius: '0px',
        },
      }}
      variant="temporary"
      anchor="right"
      open={open}
      ModalProps={{ keepMounted: true }}
      onClose={handleDrawerOpen}
    >
      {open && (
        <>
          {selectedData! && (
            <>
              <Box sx={{ p: 3 }}>
                <Grid container alignItems="center" spacing={0.5} justifyContent="space-between">
                  <Grid sx={{ width: 'calc(100% - 50px)' }}>
                    <Stack direction="row" spacing={0.5} alignItems="center">
                      <Button
                        variant="text"
                        color="error"
                        sx={{ p: 0.5, minWidth: 32, display: { xs: 'block', md: 'none' } }}
                        onClick={handleDrawerOpen}
                      >
                        <HighlightOffIcon />
                      </Button>
                      <Typography
                        variant="h4"
                        sx={{
                          display: 'inline-block',
                          width: 'calc(100% - 34px)',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          verticalAlign: 'middle',
                        }}
                      >
                        {selectedData.title}
                      </Typography>
                    </Stack>
                  </Grid>

                  <Grid>
                    <Button
                      variant="text"
                      color="error"
                      sx={{ p: 0.5, minWidth: 32 }}
                      onClick={() => setOpenModal(true)}
                    >
                      <DeleteTwoToneIcon />
                    </Button>
                    <AlertItemDelete
                      title={selectedData.title}
                      open={openModal}
                      handleClose={handleModalClose}
                    />
                  </Grid>
                </Grid>
              </Box>
              <Divider />
              <PerfectScrollbar component="div">
                <Box sx={{ p: 3 }}>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12 }}>
                      <EditItem
                        item={selectedData}
                        profiles={profiles}
                        userStory={userStory}
                        columns={columns}
                        handleDrawerOpen={handleDrawerOpen}
                      />
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      {commentList}
                    </Grid>
                    <Grid size={{ xs: 12 }}>
                      <AddItemComment itemId={selectedItem} />
                    </Grid>
                  </Grid>
                </Box>
              </PerfectScrollbar>
            </>
          )}
          {!selectedData! && (
            <Stack justifyContent="center" alignItems="center" sx={{ height: '100vh' }}>
              <Typography variant="h5" color="error">
                No item found
              </Typography>
            </Stack>
          )}
        </>
      )}
    </Drawer>
  );
};

export default ItemDetails;
