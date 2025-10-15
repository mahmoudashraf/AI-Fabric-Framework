// material-ui
import { Autocomplete,
  Box,
  Button,
  FormControl,
  FormControlLabel,
  Grid,
  MenuItem,
  Radio,
  RadioGroup,
  Select,
  TextField,
  Typography,
 } from '@mui/material';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { DesktopDatePicker } from '@mui/x-date-pickers/DesktopDatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';

// third-party
import { useFormik } from 'formik';
import Image from 'next/image';
import * as yup from 'yup';

// project imports
import { useKanban } from 'contexts/KanbanContext';
import { useNotifications } from 'contexts/NotificationContext';

// types
import { KanbanItem, KanbanProfile, KanbanUserStory, KanbanColumn } from 'types/kanban';
import AnimateButton from 'ui-component/extended/AnimateButton';

import ItemAttachments from './ItemAttachments';

interface Props {
  item: KanbanItem;
  profiles: KanbanProfile[];
  userStory: KanbanUserStory[];
  columns: KanbanColumn[];
  handleDrawerOpen: () => void;
}

const avatarImage = '/assets/images/users';
const validationSchema = yup.object({
  title: yup.string().required('Task title is required'),
  dueDate: yup.date(),
});

// ==============================|| KANBAN BOARD - ITEM EDIT ||============================== //

const EditItem = ({ item, profiles, userStory, columns, handleDrawerOpen }: Props) => {
  // Using Context API
  const kanbanContext = useKanban();
  const notificationContext = useNotifications();
  const { items } = kanbanContext.state;
  const itemUserStory = userStory.filter(
    story => story.itemIds.filter((itemId: string) => itemId === item.id)[0]
  )[0];
  const itemColumn = columns.filter(
    column => column.itemIds.filter(itemId => itemId === item.id)[0]
  )[0];

  const formik = useFormik({
    enableReinitialize: true,
    initialValues: {
      id: item.id,
      title: item.title,
      assign: item.assign,
      priority: item.priority,
      dueDate: item.dueDate ? new Date(item.dueDate) : new Date(),
      description: item.description,
      commentIds: item.commentIds,
      image: item.image,
      storyId: itemUserStory ? itemUserStory.id : '',
      columnId: itemColumn ? itemColumn.id : '',
      attachments: item.attachments,
    },
    validationSchema,
    onSubmit: async values => {
      const itemToEdit: KanbanItem = {
        id: values.id,
        title: values.title,
        assign: values.assign || '',
        priority: values.priority,
        dueDate: values.dueDate ? new Date(values.dueDate) : new Date(),
        description: values.description,
        commentIds: values.commentIds || [],
        image: values.image,
        attachments: values.attachments,
      };
      
      try {
        await kanbanContext.editItem(values.columnId, columns, itemToEdit, items, values.storyId, userStory);
        notificationContext.showNotification({
          message: 'Submit Success',
          variant: 'success',
          alert: {
            color: 'success',
            variant: 'filled',
          },
          close: false,
        });
        handleDrawerOpen();
      } catch (error) {
        notificationContext.showNotification({
          message: 'Failed to update item',
          variant: 'error',
          alert: {
            color: 'error',
            variant: 'filled',
          },
          close: false,
        });
      }
    },
  });

  return (
    <form onSubmit={formik.handleSubmit}>
      <LocalizationProvider dateAdapter={AdapterDateFns}>
        <Grid container spacing={3}>
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              id="title"
              name="title"
              label="Title"
              variant="outlined"
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
                    value={profiles.find(
                      (profile: KanbanProfile) => profile.id === formik.values.assign
                    )}
                    onChange={(_event, value) => {
                      formik.setFieldValue('assign', value?.id);
                    }}
                    options={profiles}
                    fullWidth
                    autoHighlight
                    getOptionLabel={option => option?.name || ''}
                    isOptionEqualToValue={option => option?.id === formik.values.assign}
                    renderOption={(props, option) => (
                      <Box component="li" sx={{ '& > img': { mr: 2, flexShrink: 0 } }} {...props}>
                        {option && (
                          <>
                            <Image
                              loading="lazy"
                              width={20}
                              height={20}
                              src={`${avatarImage}/${option.avatar}`}
                              alt=""
                              style={{
                                maxWidth: '100%',
                                height: 'auto',
                              }}
                            />
                            {option.name}
                          </>
                        )}
                      </Box>
                    )}
                    renderInput={params => (
                      <TextField
                        {...params}
                        name="assign"
                        label="Choose a assignee"
                        variant="outlined"
                        size="medium"
                        fullWidth
                        InputLabelProps={{
                          ...params.InputLabelProps,
                          className: params.InputLabelProps?.className ?? '',
                          style: params.InputLabelProps?.style || {},
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
              <Grid size={{ xs: 12, sm: 4 }}>
                <Typography variant="subtitle1">Prioritize:</Typography>
              </Grid>
              <Grid size={{ xs: 12, sm: 8 }}>
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
                      control={<Radio color="primary" sx={{ color: 'primary.main' }} />}
                      label="Low"
                    />
                    <FormControlLabel
                      value="medium"
                      control={<Radio color="warning" sx={{ color: 'warning.main' }} />}
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
              <Grid size={{ xs: 12, sm: 4 }}>
                <Typography variant="subtitle1">Due date:</Typography>
              </Grid>
              <Grid size={{ xs: 12, sm: 8 }}>
                <DesktopDatePicker
                  label="Due Date"
                  value={formik.values.dueDate}
                  format="dd/MM/yyyy"
                  onChange={date => {
                    formik.setFieldValue('dueDate', date);
                  }}
                />
              </Grid>
            </Grid>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Grid container alignItems="center" spacing={2}>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Typography variant="subtitle1">Description:</Typography>
              </Grid>
              <Grid size={{ xs: 12, sm: 8 }}>
                <TextField
                  fullWidth
                  id="description"
                  name="description"
                  multiline
                  rows={3}
                  variant="outlined"
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
              <Grid size={{ xs: 12, sm: 4 }}>
                <Typography variant="subtitle1">User Story:</Typography>
              </Grid>
              <Grid size={{ xs: 12, sm: 8 }}>
                <FormControl fullWidth>
                  <Select
                    id="storyId"
                    name="storyId"
                    displayEmpty
                    value={formik.values.storyId}
                    onChange={formik.handleChange}
                    inputProps={{ 'aria-label': 'Without label' }}
                  >
                    {userStory.map((story: KanbanUserStory, index: number) => (
                      <MenuItem key={index} value={story.id}>
                        {story.id} - {story.title}
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
                <Typography variant="subtitle1">State:</Typography>
              </Grid>
              <Grid size={{ xs: 12, sm: 8 }}>
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
                <ItemAttachments attachments={formik.values.attachments} />
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
  );
};

export default EditItem;
