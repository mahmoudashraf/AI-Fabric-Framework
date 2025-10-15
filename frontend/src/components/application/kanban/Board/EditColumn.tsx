import { OutlinedInput } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { ChangeEvent } from 'react';

// material-ui

// project imports
// Modern Context API implementation
import { useKanban } from 'contexts/KanbanContext';
import { useNotifications } from 'contexts/NotificationContext';

// types
import { KanbanColumn } from 'types/kanban';

interface Props {
  column: KanbanColumn;
}

// ==============================|| KANBAN BOARD - COLUMN EDIT ||============================== //

const EditColumn = ({ column }: Props) => {
  const theme = useTheme();
  
  // Use Context API for kanban and notifications
  const kanbanContext = useKanban();
  const notificationContext = useNotifications();
  const { columns } = kanbanContext.state;

  const handleColumnRename = (event: ChangeEvent<HTMLInputElement>) => {
    try {
      kanbanContext.editColumn(
        {
          id: column.id,
          title: event.target.value,
          itemIds: column.itemIds,
        },
        columns
      );
    } catch (error) {
      notificationContext.showNotification({
        message: 'Failed to rename column',
        variant: 'error',
        alert: {
          color: 'error',
          variant: 'filled',
        },
        close: true,
      });
    }
  };

  return (
    <>
      {column && (
        <OutlinedInput
          fullWidth
          value={column.title}
          onChange={handleColumnRename}
          sx={{
            mb: 1.5,
            '& input:focus': {
              bgcolor:
                theme.palette.mode === 'dark' ? theme.palette.dark[800] : theme.palette.grey[50],
            },
            '& input:hover': {
              bgcolor:
                theme.palette.mode === 'dark' ? theme.palette.dark[800] : theme.palette.grey[50],
            },
            '& input:hover + fieldset': {
              display: 'block',
            },
            '&, & input': { bgcolor: 'transparent' },
            '& fieldset': { display: 'none' },
            '& input:focus + fieldset': { display: 'block' },
          }}
          placeholder="title"
        />
      )}
    </>
  );
};

export default EditColumn;
