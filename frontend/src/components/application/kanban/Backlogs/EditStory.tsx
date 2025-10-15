'use client';

import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import HighlightOffIcon from '@mui/icons-material/HighlightOff';
import { Box,
  Button,
  Divider,
  Drawer,
  Grid,
  Typography,
  Autocomplete,
  FormControl,
  FormControlLabel,
  MenuItem,
  Radio,
  RadioGroup,
  Select,
  Stack,
  TextField,
 } from '@mui/material';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { DesktopDatePicker } from '@mui/x-date-pickers/DesktopDatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { useFormik } from 'formik';
import Image from 'next/image';
import { useState } from 'react';

// material-ui

// third party
import PerfectScrollbar from 'react-perfect-scrollbar';
import * as yup from 'yup';

// project imports
import { useKanban } from 'contexts/KanbanContext';
import { useNotifications } from 'contexts/NotificationContext';
import { KanbanColumn, KanbanProfile, KanbanUserStory } from 'types/kanban';
import AnimateButton from 'ui-component/extended/AnimateButton';
import AddStoryComment from './AddStoryComment';
import StoryComment from './StoryComment';

import ItemAttachments from '../Board/ItemAttachments';

import AlertStoryDelete from './AlertStoryDelete';

// assets

// types

interface Props {
  story: KanbanUserStory;
  open: boolean;
  handleDrawerOpen: () => void;
}

const avatarImage = '/assets/images/users';
const validationSchema = yup.object({
  title: yup.string().required('User story title is required'),
  dueDate: yup.date(),
});

// ==============================|| KANBAN BACKLOGS - EDIT STORY ||============================== //

const EditStory = ({ story, open, handleDrawerOpen }: Props) => {
  // Using Context API
  const kanbanContext = useKanban();
  const notificationContext = useNotifications();
  
  // Use Context API directly
  const { profiles, columns, comments, userStory, userStoryOrder } = kanbanContext.state;

  const formik = useFormik({
    enableReinitialize: true,
    initialValues: {
      id: story.id,
      title: story.title,
      assign: story.assign || '',
      columnId: story.columnId,
      priority: story.priority,
      dueDate: story.dueDate ? new Date(story.dueDate) : new Date(),
      acceptance: story.acceptance,
      description: story.description,
      commentIds: story.commentIds || [],
      image: false,
      itemIds: story.itemIds,
    },
    validationSchema,
    onSubmit: values => {
      try {
        kanbanContext.editStory(values, userStory);
        notificationContext.showNotification({
          message: 'Submit Success',
          variant: 'success',
          alert: {
            color: 'success',
            variant: 'filled',
          },
          close: true,
        });
      } catch (error) {
        notificationContext.showNotification({
          message: 'Failed to edit story',
          variant: 'error',
          alert: {
            color: 'error',
            variant: 'filled',
          },
          close: true,
        });
      }
      handleDrawerOpen();
    },
  });

  const [openModal, setOpenModal] = useState(false);
  const handleModalClose = (status: boolean) => {
    setOpenModal(false);
    if (status) {
      handleDrawerOpen();
      
      try {
        kanbanContext.deleteStory(story.id, userStory, userStoryOrder);
        notificationContext.showNotification({
          message: 'Story Deleted successfully',
          variant: 'success',
          alert: {
            color: 'success',
            variant: 'filled',
          },
          close: true,
        });
      } catch (error) {
        notificationContext.showNotification({
          message: 'Failed to delete story',
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
      onClose={() => {
        handleDrawerOpen();
        formik.resetForm();
      }}
    >
      {open && (
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
                    {story.title}
                  </Typography>
                </Stack>
              </Grid>

              <Grid>
                <Button
                  variant="text"
                  color="error"
                  sx={{ p: 0.5, minWidth: 32 }}
                  aria-label="Delete"
                  onClick={() => setOpenModal(true)}
                >
                  <DeleteTwoToneIcon />
                </Button>
                {openModal && (
                  <AlertStoryDelete
                    title={story.title}
                    open={openModal}
                    handleClose={handleModalClose}
                  />
                )}
              </Grid>
            </Grid>
          </Box>
          <Divider />
          <PerfectScrollbar component="div">
            <Box sx={{ p: 3 }}>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12 }}>
                  <form onSubmit={formik.handleSubmit}>
                    <LocalizationProvider dateAdapter={AdapterDateFns}>
                      <Grid container spacing={3}>
                        <Grid size={{ xs: 12 }}>
                          <TextField
                            fullWidth
                            id="title"
                            name="title"
                            label="Title"
                            value={formik.values.title}
                            onChange={formik.handleChange}
                            error={Boolean(formik.touched.title && formik.errors.title)}
                            helperText={formik.touched.title && formik.errors.title}
                          />
                        </Grid>
                        <Grid size={{ xs: 12 }}>
                          <Grid container alignItems="center" spacing={2}>
                            <Grid size={{ xs: 12, sm: 4 }}>
                              <Typography variant="subtitle1">Assign to:</Typography>
                            </Grid>
                            <Grid size={{ xs: 12, sm: 8 }}>
                              <Grid container justifyContent="flex-start">
                                <Autocomplete
                                  id="assign"
                                  value={
                                    profiles.find(
                                      (profile: KanbanProfile) =>
                                        profile.id === formik.values.assign
                                    ) || null
                                  }
                                  onChange={(_event, value) => {
                                    formik.setFieldValue('assign', value?.id);
                                  }}
                                  options={profiles}
                                  fullWidth
                                  autoHighlight
                                  getOptionLabel={option => option.name}
                                  isOptionEqualToValue={option =>
                                    option.id === formik.values.assign
                                  }
                                  renderOption={(props, option) => (
                                    <Box
                                      component="li"
                                      sx={{ '& > img': { mr: 2, flexShrink: 0 } }}
                                      {...props}
                                    >
                                      <Image
                                        height={20}
                                        width={20}
                                        src={`${avatarImage}/${option.avatar}`}
                                        alt=""
                                        style={{
                                          maxWidth: '100%',
                                          height: 'auto',
                                        }}
                                      />
                                      {option.name}
                                    </Box>
                                  )}
                                  renderInput={params => (
                                    <TextField
                                      {...params}
                                      size="medium"
                                      label="Choose a assignee"
                                      InputLabelProps={{
                                        ...params.InputLabelProps,
                                        className: params.InputLabelProps?.className ?? '',
                                        style: params.InputLabelProps?.style ?? {},
                                      }}
                                      inputProps={{
                                        ...params.inputProps,
                                        autoComplete: 'new-password', // disable autocomplete and autofill
                                      }}
                                    />
                                  )}
                                />
                              </Grid>
                            </Grid>
                          </Grid>
                        </Grid>
                        <Grid size={{ xs: 12 }}>
                          <Grid container alignItems="center" spacing={2}>
                            <Grid size={{ xs: 12 }}>
                              <Typography variant="subtitle1">Prioritize:</Typography>
                            </Grid>
                            <Grid size={{ xs: 12 }}>
                              <FormControl>
                                <RadioGroup
                                  row
                                  aria-label="color"
                                  value={formik.values.priority}
                                  onChange={formik.handleChange}
                                  name="priority"
                                  id="priority"
                                >
                                  <FormControlLabel
                                    value="low"
                                    control={
                                      <Radio color="primary" sx={{ color: 'primary.main' }} />
                                    }
                                    label="Low"
                                  />
                                  <FormControlLabel
                                    value="medium"
                                    control={
                                      <Radio color="warning" sx={{ color: 'warning.main' }} />
                                    }
                                    label="Medium"
                                  />
                                  <FormControlLabel
                                    value="high"
                                    control={<Radio color="error" sx={{ color: 'error.main' }} />}
                                    label="High"
                                  />
                                </RadioGroup>
                              </FormControl>
                            </Grid>
                          </Grid>
                        </Grid>
                        <Grid size={{ xs: 12 }}>
                          <Grid container alignItems="center" spacing={2}>
                            <Grid size={{ xs: 12 }}>
                              <Typography variant="subtitle1">Due date:</Typography>
                            </Grid>
                            <Grid size={{ xs: 12 }}>
                              <DesktopDatePicker
                                label="Due Date"
                                value={formik.values.dueDate}
                                format="dd/MM/yyyy"
                                onChange={date => {
                                  formik.setFieldValue('dueDate', date);
                                }}
                                slotProps={{ textField: { fullWidth: true } }}
                              />
                            </Grid>
                          </Grid>
                        </Grid>
                        <Grid size={{ xs: 12 }}>
                          <Grid container alignItems="center" spacing={2}>
                            <Grid size={{ xs: 12 }}>
                              <Typography variant="subtitle1">Acceptance:</Typography>
                            </Grid>
                            <Grid size={{ xs: 12 }}>
                              <TextField
                                fullWidth
                                id="acceptance"
                                name="acceptance"
                                multiline
                                placeholder="Enter Your Message"
                                rows={3}
                                value={formik.values.acceptance}
                                onChange={formik.handleChange}
                                error={Boolean(formik.touched.acceptance && formik.errors.acceptance)}
                                helperText={formik.touched.acceptance && formik.errors.acceptance}
                              />
                            </Grid>
                          </Grid>
                        </Grid>
                        <Grid size={{ xs: 12 }}>
                          <Grid container alignItems="center" spacing={2}>
                            <Grid size={{ xs: 12 }}>
                              <Typography variant="subtitle1">Description:</Typography>
                            </Grid>
                            <Grid size={{ xs: 12 }}>
                              <TextField
                                fullWidth
                                id="description"
                                name="description"
                                multiline
                                placeholder="Enter Your Message"
                                rows={3}
                                value={formik.values.description}
                                onChange={formik.handleChange}
                                error={Boolean(formik.touched.description && formik.errors.description)}
                                helperText={formik.touched.description && formik.errors.description}
                              />
                            </Grid>
                          </Grid>
                        </Grid>
                        <Grid size={{ xs: 12 }}>
                          <Grid container alignItems="center" spacing={2}>
                            <Grid size={{ xs: 12 }}>
                              <Typography variant="subtitle1">State:</Typography>
                            </Grid>
                            <Grid size={{ xs: 12 }}>
                              <FormControl fullWidth>
                                <Select
                                  id="columnId"
                                  name="columnId"
                                  displayEmpty
                                  value={formik.values.columnId}
                                  onChange={formik.handleChange}
                                  inputProps={{ 'aria-label': 'Without label' }}
                                >
                                  {columns.map((column: KanbanColumn, index: number) => (
                                    <MenuItem key={index} value={column.id}>
                                      {column.title}
                                    </MenuItem>
                                  ))}
                                </Select>
                              </FormControl>
                            </Grid>
                          </Grid>
                        </Grid>
                        <Grid size={{ xs: 12 }}>
                          <Grid container alignItems="center" spacing={2}>
                            <Grid size={{ xs: 12, sm: 4 }}>
                              <Typography variant="subtitle1">Attachments:</Typography>
                            </Grid>
                            <Grid size={{ xs: 12, sm: 8 }}>
                              <ItemAttachments attachments={[]} />
                            </Grid>
                          </Grid>
                        </Grid>

                        <Grid size={{ xs: 12 }}>
                          <AnimateButton>
                            <Button fullWidth variant="contained" type="submit">
                              Save
                            </Button>
                          </AnimateButton>
                        </Grid>
                      </Grid>
                    </LocalizationProvider>
                  </form>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  {story?.commentIds &&
                    [...story?.commentIds].reverse().map((commentId, index) => {
                      const commentData = comments.filter(comment => comment.id === commentId)[0];
                      if (!commentData) return null;
                      const profile = profiles.filter(item => item.id === commentData.profileId)[0];
                      return <StoryComment key={index} comment={commentData} profile={profile} />;
                    })}
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <AddStoryComment storyId={story.id} />
                </Grid>
              </Grid>
            </Box>
          </PerfectScrollbar>
        </>
      )}
    </Drawer>
  );
};
export default EditStory;
