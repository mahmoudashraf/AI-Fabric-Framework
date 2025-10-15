'use client';

// material-ui
import { Button, Grid, TextField, Stack  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// third-party
import { Chance } from 'chance';
import { ChangeEvent, KeyboardEvent, useState } from 'react';

// project imports
// Modern Context API implementation
import { useKanban } from 'contexts/KanbanContext';
import { useNotifications } from 'contexts/NotificationContext';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

const chance = new Chance();

// ==============================|| KANBAN BOARD - ADD COLUMN ||============================== //

const AddColumn = () => {
  const theme = useTheme();
  
  // Modern Context API implementation
  const kanbanContext = useKanban();
  const { showNotification } = useNotifications();

  const [title, setTitle] = useState('');
  const [isTitle, setIsTitle] = useState(false);

  const [isAddColumn, setIsAddColumn] = useState(false);
  const { columns, columnsOrder } = kanbanContext.state;
  const handleAddColumnChange = () => {
    setIsAddColumn(prev => !prev);
  };

  const addNewColumn = () => {
    1;
    if (title.length > 0) {
      const newColumn = {
        id: `${chance.integer({ min: 1000, max: 9999 })}`,
        title,
        itemIds: [],
      };

      kanbanContext.addColumn(newColumn, columns, columnsOrder);
      showNotification({
        message: 'Column Add successfully',
        variant: 'success',
      });
      setIsAddColumn(prev => !prev);
      setTitle('');
    } else {
      setIsTitle(true);
    }
  };

  const handleAddColumn = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' || event.keyCode === 13) {
      addNewColumn();
    }
  };

  const handleColumnTitle = (event: ChangeEvent<HTMLInputElement>) => {
    const newTitle = event.target.value;
    setTitle(newTitle);
    if (newTitle.length <= 0) {
      setIsTitle(true);
    } else {
      setIsTitle(false);
    }
  };

  return (
    <MainCard
      sx={{
        minWidth: 250,
        backgroundColor:
          theme.palette.mode === 'dark'
            ? theme.palette.background.default
            : theme.palette.primary.light,
        height: '100%',
      }}
      contentSX={{ p: 1.5, '&:last-of-type': { pb: 1.5 } }}
    >
      <Grid container alignItems="center" spacing={1}>
        {isAddColumn && (
          <Grid size={{ xs: 12 }}>
            <SubCard contentSX={{ p: 2, transition: 'background-color 0.25s ease-out' }}>
              <Grid container alignItems="center" spacing={0.5}>
                <Grid size={{ xs: 12 }}>
                  <TextField
                    fullWidth
                    placeholder="Add Column"
                    value={title}
                    onChange={handleColumnTitle}
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
                    onKeyUp={handleAddColumn}
                    helperText={isTitle ? 'Column title is required.' : ''}
                    error={isTitle}
                  />
                </Grid>
                <Grid size="grow" sx={{ minWidth: 0 }} />
                <Grid>
                  <Stack direction="row" alignItems="center" spacing={1}>
                    <Button
                      variant="text"
                      color="error"
                      sx={{ width: '100%' }}
                      onClick={handleAddColumnChange}
                    >
                      Cancel
                    </Button>
                    <Button variant="contained" color="primary" onClick={addNewColumn} size="small">
                      Add
                    </Button>
                  </Stack>
                </Grid>
              </Grid>
            </SubCard>
          </Grid>
        )}
        {!isAddColumn && (
          <Grid size={{ xs: 12 }}>
            <Button
              variant="text"
              color="primary"
              sx={{ width: '100%' }}
              onClick={handleAddColumnChange}
            >
              Add Column
            </Button>
          </Grid>
        )}
      </Grid>
    </MainCard>
  );
};

export default AddColumn;
