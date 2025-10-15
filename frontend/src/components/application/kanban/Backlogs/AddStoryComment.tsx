'use client';

import AddPhotoAlternateTwoToneIcon from '@mui/icons-material/AddPhotoAlternateTwoTone';
import AddToDriveTwoToneIcon from '@mui/icons-material/AddToDriveTwoTone';
import AttachFileTwoToneIcon from '@mui/icons-material/AttachFileTwoTone';
import { Box, Button, Grid, TextField  } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Chance } from 'chance';
import { ChangeEvent, KeyboardEvent, useState } from 'react';

// material-ui

// project imports
import useConfig from 'hooks/useConfig';
import { useKanban } from 'contexts/KanbanContext';
import { useNotifications } from 'contexts/NotificationContext';
// Using Context API

// third-party

// assets

// types
import { KanbanComment } from 'types/kanban';

interface Props {
  storyId: string;
}

const chance = new Chance();

// ==============================|| KANBAN BACKLOGS - ADD STORY COMMENT ||============================== //

const AddStoryComment = ({ storyId }: Props) => {
  const theme = useTheme();
  
  // Using Context API
  const kanbanContext = useKanban();
  const notificationContext = useNotifications();
  
  // Use Context API directly
  const { comments, userStory } = kanbanContext.state;
  
  const { borderRadius } = useConfig();

  const [comment, setComment] = useState('');
  const [isComment, setIsComment] = useState(false);

  const addNewStoryComment = () => {
    if (comment.length > 0) {
      const newComment: KanbanComment = {
        id: `${chance.integer({ min: 1000, max: 9999 })}`,
        comment,
        profileId: 'profile-1',
      };

      try {
        kanbanContext.addStoryComment(storyId, newComment, comments, userStory);
        notificationContext.showNotification({
          message: 'Comment Add successfully',
          variant: 'success',
          alert: {
            color: 'success',
            variant: 'filled',
          },
          close: false,
        });
      } catch (error) {
        notificationContext.showNotification({
          message: 'Failed to add comment',
          variant: 'error',
          alert: {
            color: 'error',
            variant: 'filled',
          },
          close: false,
        });
      }
      setComment('');
    } else {
      setIsComment(true);
    }
  };

  const handleAddStoryComment = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' || event.keyCode === 13) {
      addNewStoryComment();
    }
  };

  const handleStoryComment = (event: ChangeEvent<HTMLInputElement>) => {
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
            : theme.palette.primary[200],
        borderRadius: `${borderRadius}px`,
      }}
    >
      <Grid container alignItems="center" spacing={0.5}>
        <Grid size={{ xs: 12 }}>
          <TextField
            fullWidth
            placeholder="Add Comment"
            value={comment}
            onChange={handleStoryComment}
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
            onKeyUp={handleAddStoryComment}
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
            aria-label="add file attachment"
          >
            <AttachFileTwoToneIcon />
          </Button>
        </Grid>
        <Grid>
          <Button
            variant="text"
            color="primary"
            sx={{ p: 0.5, minWidth: 32 }}
            aria-label="add file or any item to google drive"
          >
            <AddToDriveTwoToneIcon />
          </Button>
        </Grid>
        <Grid size="grow" sx={{ minWidth: 0 }} />
        <Grid>
          <Button variant="contained" color="primary" onClick={addNewStoryComment}>
            Comment
          </Button>
        </Grid>
      </Grid>
    </Box>
  );
};

export default AddStoryComment;
