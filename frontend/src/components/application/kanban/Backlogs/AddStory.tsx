// material-ui
import { Box,
  Button,
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
  TextField,
 } from '@mui/material';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';
import { DesktopDatePicker } from '@mui/x-date-pickers/DesktopDatePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';

// third party
import { Chance } from 'chance';
import { useFormik } from 'formik';
import Image from 'next/image';
import * as yup from 'yup';

// project imports
import { useKanban } from 'contexts/KanbanContext';
import { useNotifications } from 'contexts/NotificationContext';
// Using Context API

// types
import { KanbanColumn, KanbanProfile } from 'types/kanban';
import AnimateButton from 'ui-component/extended/AnimateButton';

import ItemAttachments from '../Board/ItemAttachments';

interface Props {
  open: boolean;
  handleDrawerOpen: () => void;
}

const chance = new Chance();
const avatarImage = '/assets/images/users';
const validationSchema = yup.object({
  title: yup.string().required('User story title is required'),
  dueDate: yup.date(),
});

// ==============================|| KANBAN BACKLOGS - ADD STORY ||============================== //

const AddStory = ({ open, handleDrawerOpen }: Props) => {
  // Using Context API
  const kanbanContext = useKanban();
  const notificationContext = useNotifications();
  
  // Use Context API directly
  const { profiles, columns, userStory, userStoryOrder } = kanbanContext.state;

  const formik = useFormik({
    initialValues: {
      id: `${chance.integer({ min: 1000, max: 9999 })}`,
      title: '',
      assign: null,
      columnId: '',
      priority: 'low',
      dueDate: new Date(),
      acceptance: '',
      description: '',
      commentIds: '',
      image: false,
      itemIds: [],
    },
    validationSchema,
    onSubmit: values => {
      try {
        kanbanContext.addStory(values, userStory, userStoryOrder);
        notificationContext.showNotification({
          message: 'Submit Success',
          variant: 'success',
          alert: {
            color: 'success',
            variant: 'filled',
          },
          close: false,
        });
      } catch (error) {
        notificationContext.showNotification({
          message: 'Failed to add story',
          variant: 'error',
          alert: {
            color: 'error',
            variant: 'filled',
          },
          close: false,
        });
      }
      handleDrawerOpen();
    },
  });

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
      onClose={handleDrawerOpen}
    >
      {open && (
        <Box sx={{ p: 3 }}>
          <form onSubmit={formik.handleSubmit}>
            <LocalizationProvider dateAdapter={AdapterDateFns}>
              <Grid container spacing={3}>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="h4">Add Story</Typography>
                </Grid>
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
                              (profile: KanbanProfile) => profile.id === formik.values.assign
                            ) || null
                          }
                          onChange={(_event, value) => {
                            formik.setFieldValue('assign', value?.id);
                          }}
                          options={profiles}
                          fullWidth
                          autoHighlight
                          getOptionLabel={option => option.name}
                          isOptionEqualToValue={option => option.id === formik.values.assign}
                          renderOption={(props, option) => (
                            <Box
                              component="li"
                              sx={{ '& > img': { mr: 2, flexShrink: 0 } }}
                              {...props}
                            >
                              <Image
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
                        slotProps={{ textField: { fullWidth: true } }}
                      />
                    </Grid>
                  </Grid>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Grid container alignItems="center" spacing={2}>
                    <Grid size={{ xs: 12, sm: 4 }}>
                      <Typography variant="subtitle1">Acceptance:</Typography>
                    </Grid>
                    <Grid size={{ xs: 12, sm: 8 }}>
                      <TextField
                        fullWidth
                        id="acceptance"
                        name="acceptance"
                        multiline
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
        </Box>
      )}
    </Drawer>
  );
};
export default AddStory;
