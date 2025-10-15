'use client';

// material-ui

// third-party
import { DragDropContext, Droppable, DropResult } from '@hello-pangea/dnd';
import { Box } from '@mui/material';

// project imports
import AddColumn from 'components/application/kanban/Board/AddColumn';
import Columns from 'components/application/kanban/Board/Columns';
import ItemDetails from 'components/application/kanban/Board/ItemDetails';
import Container from 'components/application/kanban/Container';
// Using Context API
import { useKanban } from 'contexts/KanbanContext';

// types
import { KanbanColumn } from 'types/kanban';
import MainCard from 'ui-component/cards/MainCard';

const getDragWrapper = (isDraggingOver: boolean) => ({
  p: 1,
  bgcolor: isDraggingOver ? 'primary.200' : 'transparent',
  display: 'flex',
  overflow: 'auto',
});

// ==============================|| KANBAN - BOARD ||============================== //

const Board = () => {
  // Using Context API
  const { state, updateColumnOrder, updateColumnItemOrder } = useKanban();
  const { columns, columnsOrder } = state;
  // handle drag & drop
  const onDragEnd = (result: DropResult) => {
    const { source, destination, draggableId, type } = result;

    if (!destination) return;
    if (destination.droppableId === source.droppableId && destination.index === source.index)
      return;

    if (type === 'column') {
      const newColumnsOrder = Array.from(columnsOrder);

      newColumnsOrder.splice(source.index, 1); // remove dragged column
      newColumnsOrder.splice(destination?.index, 0, draggableId); // set column new position

      updateColumnOrder(newColumnsOrder);
      return;
    }

    // find dragged item's column
    const sourceColumn = columns.filter(item => item.id === source.droppableId)[0];

    // find dropped item's column
    const destinationColumn = columns.filter(item => item.id === destination.droppableId)[0];

    let newColumn: KanbanColumn[] = columns; // Initialize with current columns

    // if - moving items in the same list
    // else - moving items from one list to another
    if (sourceColumn === destinationColumn && sourceColumn) {
      const newItemIds = Array.from(sourceColumn.itemIds);

      // remove the id of dragged item from its original position
      newItemIds.splice(source.index, 1);

      // insert the id of dragged item to the new position
      newItemIds.splice(destination.index, 0, draggableId);

      // updated column
      const newSourceColumn: KanbanColumn = {
        id: sourceColumn.id,
        title: sourceColumn.title,
        itemIds: newItemIds,
      };

      newColumn = columns.map(column => {
        if (column.id === newSourceColumn.id) {
          return newSourceColumn;
        }
        return column;
      });
    } else if (sourceColumn && destinationColumn) {
      const newSourceItemIds = Array.from(sourceColumn.itemIds);

      // remove the id of dragged item from its original column
      newSourceItemIds.splice(source.index, 1);

      // updated dragged items's column
      const newSourceColumn: KanbanColumn = {
        id: sourceColumn.id,
        title: sourceColumn.title,
        itemIds: newSourceItemIds,
      };

      const newDestinationItemIds = Array.from(destinationColumn.itemIds);

      // insert the id of dragged item to the new position in dropped column
      newDestinationItemIds.splice(destination.index, 0, draggableId);

      // updated dropped item's column
      const newDestinationColumn: KanbanColumn = {
        id: destinationColumn.id,
        title: destinationColumn.title,
        itemIds: newDestinationItemIds,
      };

      newColumn = columns.map(column => {
        if (column.id === newSourceColumn.id) {
          return newSourceColumn;
        }
        if (column.id === newDestinationColumn.id) {
          return newDestinationColumn;
        }
        return column;
      });
    }

    updateColumnItemOrder(newColumn);
  };

  return (
    <Container>
      <Box sx={{ display: 'flex' }}>
        <DragDropContext onDragEnd={onDragEnd}>
          <Droppable droppableId="columns" direction="horizontal" type="column">
            {(provided, snapshot) => (
              <MainCard
                border={false}
                ref={provided.innerRef}
                contentSX={getDragWrapper(snapshot.isDraggingOver)}
                {...provided.droppableProps}
              >
                {columnsOrder.map((columnId, index) => {
                  const column = columns.filter(item => item.id === columnId)[0];
                  if (!column) return null;
                  return <Columns key={columnId} column={column} index={index} />;
                })}
                {provided.placeholder}
                <AddColumn />
              </MainCard>
            )}
          </Droppable>
        </DragDropContext>
        <ItemDetails />
      </Box>
    </Container>
  );
};

// Enterprise Pattern: Apply error boundary HOC
import { withErrorBoundary } from '@/components/enterprise';
export default withErrorBoundary(Board);
