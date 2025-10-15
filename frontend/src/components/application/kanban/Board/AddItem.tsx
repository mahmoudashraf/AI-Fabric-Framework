'use client';

import CalendarTodayTwoToneIcon from '@mui/icons-material/CalendarTodayTwoTone';
import CloseIcon from '@mui/icons-material/Close';
import PeopleAltTwoToneIcon from '@mui/icons-material/PeopleAltTwoTone';
import { Button, Grid, IconButton, TextField, Stack  } from '@mui/material';
import { Chance } from 'chance';
import { sub } from 'date-fns';
import { ChangeEvent, KeyboardEvent, useState } from 'react';

// material-ui

// third-party

// project imports
import { useKanban } from 'contexts/KanbanContext';
import { useNotifications } from 'contexts/NotificationContext';
// Using Context API
import { KanbanItem } from 'types/kanban';
import SubCard from 'ui-component/cards/SubCard';

// assets

// types

interface Props {
  columnId: string;
}

const chance = new Chance();

// ==============================|| KANBAN BOARD - ADD ITEM ||============================== //

const AddItem = ({ columnId }: Props) => {
  // Modern Context API implementation
  const kanbanContext = useKanban();
  const notificationContext = useNotifications();

  const [addTaskBox, setAddTaskBox] = useState(false);
  const handleAddTaskChange = () => {
    setAddTaskBox(prev => !prev);
  };

  const [title, setTitle] = useState('');
  const [isTitle, setIsTitle] = useState(false);

  const addTask = () => {
    if (title.length > 0) {
      const newItem: KanbanItem = {
        id: `${chance.integer({ min: 1000, max: 9999 })}`,
        title,
        dueDate: sub(new Date(), { days: 0, hours: 1, minutes: 45 }),
        image: false,
        assign: '',
        description: '',
        priority: 'low',
        attachments: [],
      };

      // Use modern Context API for kanban management
      try {
        kanbanContext.addItem(
          columnId, 
          kanbanContext.state.columns, 
          newItem, 
          kanbanContext.state.items, 
          '0', 
          kanbanContext.state.userStory
        );
        notificationContext.showNotification({
          message: 'Task Add successfully',
          variant: 'success',
          alert: {
            color: 'success',
            variant: 'filled',
          },
          close: true,
        });
      } catch (error) {
        notificationContext.showNotification({
          message: 'Failed to add task',
          variant: 'error',
          alert: {
            color: 'error',
            variant: 'filled',
          },
          close: true,
        });
      }
      handleAddTaskChange();
      setTitle('');
    } else {
      setIsTitle(true);
    }
  };

  const handleAddTask = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' || event.keyCode === 13) {
      addTask();
    }
  };

  const handleTaskTitle = (event: ChangeEvent<HTMLInputElement>) => {
    const newTitle = event.target.value;
    setTitle(newTitle);
    if (newTitle.length <= 0) {
      setIsTitle(true);
    } else {
      setIsTitle(false);
    }
  };

  return (
    <Grid container alignItems="center" spacing={1} sx={{ marginTop: 1 }}>
      {addTaskBox && (
        <Grid size={{ xs: 12 }}>
          <SubCard contentSX={{ p: 2, transition: 'background-color 0.25s ease-out' }}>
            <Grid container alignItems="center" spacing={0.5}>
              <Grid size={{ xs: 12 }}>
                <TextField
                  fullWidth
                  placeholder="Add Task"
                  value={title}
                  onChange={handleTaskTitle}
                  sx={{
                    mb: 2,
                    '& input': { bgcolor: 'transparent', p: 0, borderRadius: '0px' },
                    '& fieldset': { display: 'none' },
                    '& .MuiFormHelperText-root': {
                      ml: 0,
                    },
                    '& .MuiOutlinedInput-root': {
                      bgcolor: 'transparent',
                    },
                  }}
                  onKeyUp={handleAddTask}
                  helperText={isTitle ? 'Task title is required.' : ''}
                  error={isTitle}
                />
              </Grid>
              <Grid>
                <Button
                  variant="text"
                  size="small"
                  color="primary"
                  sx={{ p: 0.5, minWidth: 32 }}
                  aria-label="people"
                >
                  <PeopleAltTwoToneIcon fontSize="small" />
                </Button>
              </Grid>
              <Grid>
                <Button
                  variant="text"
                  size="small"
                  color="primary"
                  sx={{ p: 0.5, minWidth: 32 }}
                  aria-label="calendar"
                >
                  <CalendarTodayTwoToneIcon fontSize="small" />
                </Button>
              </Grid>
              <Grid size="grow" sx={{ minWidth: 0 }} />
              <Grid>
                <Stack direction="row" alignItems="center" spacing={0.5}>
                  <IconButton
                    size="small"
                    color="error"
                    onClick={handleAddTaskChange}
                    aria-label="cancel add task"
                  >
                    <CloseIcon fontSize="small" />
                  </IconButton>
                  <Button variant="contained" color="primary" onClick={addTask} size="small">
                    Add
                  </Button>
                </Stack>
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
      )}
      {!addTaskBox && (
        <Grid size={{ xs: 12 }}>
          <Button
            variant="text"
            color="primary"
            sx={{ width: '100%' }}
            onClick={handleAddTaskChange}
          >
            Add Task
          </Button>
        </Grid>
      )}
    </Grid>
  );
};

export default AddItem;
