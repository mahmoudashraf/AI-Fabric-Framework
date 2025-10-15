'use client';
import { DragDropContext, DropResult, Droppable } from '@hello-pangea/dnd';
import AddIcon from '@mui/icons-material/Add';
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
} from '@mui/material';
import { Theme, useTheme } from '@mui/material/styles';
import { useState } from 'react';

// material-ui

// third-party

// project import
import AddStory from 'components/application/kanban/Backlogs/AddStory';
import UserStory from 'components/application/kanban/Backlogs/UserStory';
import ItemDetails from 'components/application/kanban/Board/ItemDetails';
import Container from 'components/application/kanban/Container';
import { useKanban } from 'contexts/KanbanContext';
import { useNotifications } from 'contexts/NotificationContext';

// Using Context API

// assets

// types
import { KanbanUserStory } from 'types/kanban';

const getDropWrapper = (isDraggingOver: boolean, theme: Theme) => ({
  background: isDraggingOver ? theme.palette.secondary.light + 50 : 'transparent',
});

// ==============================|| KANBAN - BACKLOGS ||============================== //

const Backlogs = () => {
  const theme = useTheme();
  
  // Using Context API
  const kanbanContext = useKanban();
  const notificationContext = useNotifications();
  
  // Use Context API directly
  const { userStory, userStoryOrder } = kanbanContext.state;

  const onDragEnd = (result: DropResult) => {
    let newUserStory: KanbanUserStory[];
    const { source, destination, draggableId, type } = result;

    if (!destination) return;

    if (destination.droppableId === source.droppableId && destination.index === source.index)
      return;

    if (type === 'user-story') {
      const newUserStoryOrder = Array.from(userStoryOrder);

      newUserStoryOrder.splice(source.index, 1); // remove dragged column
      newUserStoryOrder.splice(destination?.index, 0, draggableId); // set column new position
      try {
        kanbanContext.updateStoryOrder(newUserStoryOrder);
      } catch (error) {
        notificationContext.showNotification({
          message: 'Failed to update story order',
          variant: 'error',
          alert: { color: 'error', variant: 'filled' },
          close: true,
        });
      }
      return;
    }

    // find dragged item's column
    const sourceUserStory = userStory.find(story => story.id === source.droppableId);
    const destinationUserStory = userStory.find(story => story.id === destination.droppableId);

    // Return early if stories not found
    if (!sourceUserStory || !destinationUserStory) return;

    // if - moving items in the same list
    // else - moving items from one list to another
    if (sourceUserStory === destinationUserStory) {
      const newItemIds = Array.from(sourceUserStory.itemIds);

      // remove the id of dragged item from its original position
      newItemIds.splice(source.index, 1);

      // insert the id of dragged item to the new position
      newItemIds.splice(destination.index, 0, draggableId);

      // updated column
      const newSourceUserStory = {
        ...sourceUserStory,
        itemIds: newItemIds,
      };

      newUserStory = userStory.map(story => {
        if (story.id === newSourceUserStory.id) {
          return newSourceUserStory;
        }
        return story;
      });
    } else {
      const newSourceItemIds = Array.from(sourceUserStory.itemIds);

      // remove the id of dragged item from its original column
      newSourceItemIds.splice(source.index, 1);

      // updated dragged items's column
      const newSourceUserStory = {
        ...sourceUserStory,
        itemIds: newSourceItemIds,
      };

      const newDestinationItemIds = Array.from(destinationUserStory.itemIds);

      // insert the id of dragged item to the new position in dropped column
      newDestinationItemIds.splice(destination.index, 0, draggableId);

      // updated dropped item's column
      const newDestinationSourceUserStory = {
        ...destinationUserStory,
        itemIds: newDestinationItemIds,
      };

      newUserStory = userStory.map(story => {
        if (story.id === newSourceUserStory.id) {
          return newSourceUserStory;
        }
        if (story.id === newDestinationSourceUserStory.id) {
          return newDestinationSourceUserStory;
        }
        return story;
      });
    }

    try {
      kanbanContext.updateStoryItemOrder(newUserStory);
    } catch (error) {
      notificationContext.showNotification({
        message: 'Failed to update story item order',
        variant: 'error',
        alert: { color: 'error', variant: 'filled' },
        close: true,
      });
    }
  };

  // drawer
  const [openDrawer, setOpenDrawer] = useState<boolean>(false);
  const handleDrawerOpen = () => {
    setOpenDrawer(prevState => !prevState);
  };

  const addStory = () => {
    setOpenDrawer(prevState => !prevState);
  };

  return (
    <Container>
      <TableContainer>
        <DragDropContext onDragEnd={onDragEnd}>
          <Droppable droppableId="user-story" type="user-story">
            {(provided, snapshot) => (
              <Table
                size="small"
                aria-label="collapsible table"
                {...provided.droppableProps}
                ref={provided.innerRef}
                sx={getDropWrapper(snapshot.isDraggingOver, theme)}
              >
                <TableHead sx={{ '& th,& td': { whiteSpace: 'nowrap' } }}>
                  <TableRow>
                    <TableCell sx={{ pl: 3 }}>
                      <Tooltip title="Add User Story">
                        <Button
                          variant="contained"
                          size="small"
                          color="secondary"
                          onClick={addStory}
                          endIcon={<AddIcon />}
                        >
                          ADD
                        </Button>
                      </Tooltip>
                    </TableCell>
                    <TableCell>Id</TableCell>
                    <TableCell>Title</TableCell>
                    <TableCell />
                    <TableCell>State</TableCell>
                    <TableCell>Assigned To</TableCell>
                    <TableCell>Priority</TableCell>
                    <TableCell>Due Date</TableCell>
                  </TableRow>
                </TableHead>

                <TableBody sx={{ '& th,& td': { whiteSpace: 'nowrap' } }}>
                  {userStoryOrder.map((storyId: string, index: number) => {
                    const story = userStory.find(
                      (item: KanbanUserStory) => item.id === storyId
                    );
                    if (!story) return null;
                    return <UserStory key={story.id} story={story} index={index} />;
                  })}
                  {provided.placeholder}
                </TableBody>
              </Table>
            )}
          </Droppable>
        </DragDropContext>
        <AddStory open={openDrawer} handleDrawerOpen={handleDrawerOpen} />
        <ItemDetails />
      </TableContainer>
    </Container>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(Backlogs);
