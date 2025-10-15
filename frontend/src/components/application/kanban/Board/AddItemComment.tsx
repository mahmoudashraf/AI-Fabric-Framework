'use client';

import AddPhotoAlternateTwoToneIcon from '@mui/icons-material/AddPhotoAlternateTwoTone';
import AddToDriveTwoToneIcon from '@mui/icons-material/AddToDriveTwoTone';
import AttachFileTwoToneIcon from '@mui/icons-material/AttachFileTwoTone';
import { Box, Button, Grid, TextField  } from '@mui/material';
import { useTheme, alpha } from '@mui/material/styles';
import { Chance } from 'chance';
import { ChangeEvent, KeyboardEvent, useState } from 'react';

// material-ui

// third-party

// project imports
import useConfig from 'hooks/useConfig';
// Modern Context API implementation
import { useKanban } from 'contexts/KanbanContext';
import { useNotifications } from 'contexts/NotificationContext';

// assets

// types
import { KanbanComment } from 'types/kanban';

interface Props {
  itemId: string | false;
}

const chance = new Chance();

// ==============================|| KANBAN BOARD - ADD ITEM COMMENT ||============================== //

const AddItemComment = ({ itemId }: Props) => {
  const theme = useTheme();
  
  // Modern Context API implementation
  const kanbanContext = useKanban();
  const notificationContext = useNotifications();
  const { borderRadius } = useConfig();
  const { comments, items } = kanbanContext.state;

  const [comment, setComment] = useState('');
  const [isComment, setIsComment] = useState(false);

  const addTaskComment = () => {
    if (comment.length > 0) {
      const newComment: KanbanComment = {
        id: `${chance.integer({ min: 1000, max: 9999 })}`,
        comment,
        profileId: 'profile-1',
      };

      kanbanContext.addItemComment(itemId, newComment, items, comments);
      notificationContext.showNotification({
        message: 'Comment Add successfully',
        variant: 'success',
        alert: {
          color: 'success',
          variant: 'filled',
        },
        close: true,
      });

      setComment('');
    } else {
      setIsComment(true);
    }
  };

  const handleAddTaskComment = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' || event.keyCode === 13) {
      addTaskComment();
    }
  };

  const handleTaskComment = (event: ChangeEvent<HTMLInputElement>) => {
    const newComment = event.target.value;
    setComment(newComment);
    if (newComment.length <= 0) {
      setIsComment(true);
    } else {
      setIsComment(false);
    }
  };

  return (
    <Box
      sx={{
        p: 2.5,
        border: '1px solid',
        borderColor:
          theme.palette.mode === 'dark'
            ? theme.palette.background.default
            : alpha(theme.palette.primary.main, 0.46),
        borderRadius: `${borderRadius}px`,
      }}
    >
      <Grid container alignItems="center" spacing={0.5}>
        <Grid size={{ xs: 12 }}>
          <TextField
            fullWidth
            placeholder="Add Comment"
            value={comment}
            onChange={handleTaskComment}
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
            onKeyUp={handleAddTaskComment}
            helperText={isComment ? 'Comment is required.' : ''}
            error={isComment}
          />
        </Grid>
        <Grid>
          <Button
            variant="text"
            color="primary"
            sx={{ p: 0.5, minWidth: 32 }}
            aria-label="add photo"
          >
            <AddPhotoAlternateTwoToneIcon />
          </Button>
        </Grid>
        <Grid>
          <Button
            variant="text"
            color="primary"
            sx={{ p: 0.5, minWidth: 32 }}
            aria-label="attachment"
          >
            <AttachFileTwoToneIcon />
          </Button>
        </Grid>
        <Grid>
          <Button
            variant="text"
            color="primary"
            sx={{ p: 0.5, minWidth: 32 }}
            aria-label="add file for your google drive"
          >
            <AddToDriveTwoToneIcon />
          </Button>
        </Grid>
        <Grid size="grow" sx={{ minWidth: 0 }} />
        <Grid>
          <Button variant="contained" color="primary" onClick={addTaskComment}>
            Comment
          </Button>
        </Grid>
      </Grid>
    </Box>
  );
};

export default AddItemComment;
