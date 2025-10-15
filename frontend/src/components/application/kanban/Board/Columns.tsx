'use client';

// material-ui

// third-party
import { Droppable, Draggable, DraggingStyle, NotDraggingStyle } from '@hello-pangea/dnd';

// project imports
// Modern Context API implementation
import { useKanban } from 'contexts/KanbanContext';
import { useNotifications } from 'contexts/NotificationContext';

// assets & UI components
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import { Grid, IconButton  } from '@mui/material';
import { useTheme, Theme } from '@mui/material/styles';
import { CSSProperties, useState } from 'react';
import useConfig from 'hooks/useConfig';
import { gridSpacing } from 'constants/index';

// types
import { KanbanColumn } from 'types/kanban';

import AddItem from './AddItem';
import AlertColumnDelete from './AlertColumnDelete';
import EditColumn from './EditColumn';
import Items from './Items';

interface Props {
  column: KanbanColumn;
  index: number;
}

// column drag wrapper
const getDragWrapper = (
  isDragging: boolean,
  draggableStyle: DraggingStyle | NotDraggingStyle | undefined,
  theme: Theme,
  radius: string
): CSSProperties | undefined => {
  const bgcolor =
    theme.palette.mode === 'dark' ? theme.palette.background.default : theme.palette.primary.light;
  return {
    minWidth: 250,
    border: '1px solid',
    borderColor:
      theme.palette.mode === 'dark'
        ? theme.palette.background.default
        : (theme.palette.primary[200] ?? theme.palette.primary.main) + 75,
    backgroundColor: isDragging ? theme.palette.grey[50] : bgcolor,
    borderRadius: radius,
    userSelect: 'none',
    margin: `0 ${16}px 0 0`,
    height: '100%',
    ...draggableStyle,
  };
};

// column drop wrapper
const getDropWrapper = (isDraggingOver: boolean, theme: Theme, radius: string) => {
  const bgcolor =
    theme.palette.mode === 'dark' ? theme.palette.background.default : theme.palette.primary.light;
  const bgcolorDrop =
    theme.palette.mode === 'dark' ? theme.palette.text.disabled : theme.palette.primary[200];

  return {
    background: isDraggingOver ? bgcolorDrop : bgcolor,
    padding: '8px 16px 14px',
    width: 'auto',
    borderRadius: radius,
  };
};

// ==============================|| KANBAN BOARD - COLUMN ||============================== //

const Columns = ({ column, index }: Props) => {
  const theme = useTheme();
  
  // Using Context API
  const kanbanContext = useKanban();
  const notificationContext = useNotifications();
  
  const { borderRadius } = useConfig();
  
  // Modern Context API implementation
  const { items, columns, columnsOrder } = kanbanContext.state;

  const columnItems =
    column &&
    column.itemIds &&
    column.itemIds.map(itemId => items.filter((item: any) => item.id === itemId)[0]);

  const [open, setOpen] = useState(false);

  const handleColumnDelete = () => {
    setOpen(true);
  };

  const handleClose = async (status: boolean) => {
    setOpen(false);
    if (status) {
      try {
        // Use Context API directly
        kanbanContext.deleteColumn(column.id, columnsOrder, columns);
        notificationContext.showNotification({
          message: 'Column deleted successfully',
          anchorOrigin: { vertical: 'top', horizontal: 'right' },
          variant: 'success',
          alert: {
            color: 'success',
            variant: 'filled',
          },
          close: false,
        });
      } catch (error) {
        notificationContext.showNotification({
          message: 'Failed to delete column',
          anchorOrigin: { vertical: 'top', horizontal: 'right' },
          variant: 'success',
          alert: {
            color: 'error',
            variant: 'filled',
          },
          close: false,
        });
      }
    }
  };

  return (
    <Draggable draggableId={column.id} index={index}>
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
          <Droppable droppableId={column.id} type="item">
            {(providedDrop, snapshotDrop) => (
              <div
                ref={providedDrop.innerRef}
                {...providedDrop.droppableProps}
                style={getDropWrapper(snapshotDrop.isDraggingOver, theme, `${borderRadius}px`)}
              >
                <Grid container alignItems="center" spacing={gridSpacing}>
                  <Grid size="grow" sx={{ minWidth: 0 }}>
                    <EditColumn column={column} />
                  </Grid>
                  <Grid sx={{ mb: 1.5 }}>
                    <IconButton
                      onClick={handleColumnDelete}
                      aria-label="Delete Columns"
                      size="large"
                    >
                      <DeleteTwoToneIcon
                        fontSize="small"
                        aria-controls="menu-simple-card"
                        aria-haspopup="true"
                      />
                    </IconButton>
                    {open && (
                      <AlertColumnDelete
                        title={column.title}
                        open={open}
                        handleClose={handleClose}
                      />
                    )}
                  </Grid>
                </Grid>
                {columnItems?.filter(Boolean).map((item, i) => (
                  item ? <Items key={i} item={item} index={i} /> : null
                ))}
                {providedDrop.placeholder}
                <AddItem columnId={column.id} />
              </div>
            )}
          </Droppable>
        </div>
      )}
    </Draggable>
  );
};

export default Columns;
