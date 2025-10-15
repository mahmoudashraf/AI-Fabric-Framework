// material-ui
import { Button,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  FormHelperText,
  IconButton,
  InputAdornment,
  Radio,
  RadioGroup,
  Stack,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { Grid } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { LocalizationProvider, MobileDateTimePicker } from '@mui/x-date-pickers';
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns';

// third-party
import { useFormik, Form, FormikProvider, FormikValues } from 'formik';
import _ from 'lodash';
import * as Yup from 'yup';

// project imports
import { gridSpacing } from 'constants/index';
import { DateRange } from 'types';

import ColorPalette from './ColorPalette';

// types

// assets
import DateRangeIcon from '@mui/icons-material/DateRange';
import DeleteIcon from '@mui/icons-material/Delete';

// constant
const getInitialValues = (event: FormikValues | null, range: DateRange | null) => {
  const newEvent = {
    title: '',
    description: '',
    color: '#2196f3',
    textColor: '',
    allDay: false,
    start: range ? new Date(range.start) : new Date(),
    end: range ? new Date(range.end) : new Date(),
  };

  if (event || range) {
    return _.merge({}, newEvent, event);
  }

  return newEvent;
};

// ==============================|| CALENDAR EVENT ADD / EDIT / DELETE ||============================== //

interface AddEventFormProps {
  event: FormikValues | null;
  range: DateRange | null;
  handleDelete: (i: string) => void;
  handleCreate: (d: FormikValues) => void;
  handleUpdate: (i: string, d: FormikValues) => void;
  onCancel: () => void;
}

const AddEventFrom = ({
  event,
  range,
  handleDelete,
  handleCreate,
  handleUpdate,
  onCancel,
}: AddEventFormProps) => {
  const theme = useTheme();
  const isCreating = !event;

  const backgroundColor = [
    {
      value: theme.palette.primary.main,
      color: 'primary.main',
      label: 'Default',
    },
    {
      value: theme.palette.error.main,
      color: 'error.main',
    },
    {
      value: theme.palette.success.dark,
      color: 'success.dark',
    },
    {
      value: theme.palette.secondary.main,
      color: 'secondary.main',
    },
    {
      value: theme.palette.warning.dark,
      color: 'warning.dark',
    },
    {
      value: (theme.palette as any).orange?.dark ?? theme.palette.warning.dark,
      color: 'warning.dark',
    },
    {
      value: theme.palette.grey[500],
      color: 'grey.500',
    },
    {
      value: theme.palette.primary.light,
      color: 'primary.light',
    },
    {
      value: theme.palette.error.light,
      color: 'error.light',
    },
    {
      value: theme.palette.success.light,
      color: 'success.light',
    },
    {
      value: theme.palette.secondary.light,
      color: 'secondary.light',
    },
    {
      value: theme.palette.warning.light,
      color: 'warning.light',
    },
    {
      value: (theme.palette as any).orange?.light ?? theme.palette.warning.light,
      color: 'warning.light',
    },
  ];

  const textColor = [
    {
      value: theme.palette.error.light,
      color: 'error.light',
      label: '',
    },
    {
      value: theme.palette.success.light,
      color: 'success.light',
      label: '',
    },
    {
      value: theme.palette.secondary.light,
      color: 'secondary.light',
      label: '',
    },
    {
      value: theme.palette.warning.light,
      color: 'warning.light',
      label: '',
    },
    {
      value: (theme.palette as any).orange?.light ?? theme.palette.warning.light,
      color: 'warning.light',
      label: '',
    },
    {
      value: theme.palette.primary.light,
      color: 'primary.light',
      label: '',
    },
    {
      value: theme.palette.primary.main,
      color: 'primary.main',
      label: '',
    },
    {
      value: theme.palette.error.main,
      color: 'error.main',
      label: '',
    },
    {
      value: theme.palette.success.dark,
      color: 'success.dark',
      label: '',
    },
    {
      value: theme.palette.secondary.main,
      color: 'secondary.main',
      label: '',
    },
    {
      value: theme.palette.warning.dark,
      color: 'warning.dark',
      label: '',
    },
    {
      value: (theme.palette as any).orange?.dark ?? theme.palette.warning.dark,
      color: 'warning.dark',
      label: '',
    },
    {
      value: theme.palette.grey[500],
      color: 'grey.500',
      label: '',
    },
  ];

  const EventSchema = Yup.object().shape({
    title: Yup.string().max(255).required('Title is required'),
    description: Yup.string().max(5000),
    end: Yup.date().when(
      'start',
      (start, schema) => start && schema.min(start, 'End date must be later than start date')
    ),
    start: Yup.date(),
    color: Yup.string().max(255),
    textColor: Yup.string().max(255),
  });

  const formik = useFormik({
    initialValues: getInitialValues(event, range),
    validationSchema: EventSchema,
    onSubmit: async (values, { resetForm, setSubmitting }) => {
      try {
        const data = {
          title: values.title,
          description: values.description,
          color: values.color,
          textColor: values.textColor,
          allDay: values.allDay,
          start: values.start,
          end: values.end,
        };

        if (event) {
          handleUpdate(event.id, data);
        } else {
          handleCreate(data);
        }

        resetForm();
        onCancel();
        setSubmitting(false);
      } catch (error) {
        console.error(error);
      }
    },
  });

  const { values, errors, touched, handleSubmit, isSubmitting, getFieldProps, setFieldValue } =
    formik;

  return (
    <FormikProvider value={formik}>
      <LocalizationProvider dateAdapter={AdapterDateFns}>
        <Form autoComplete="off" noValidate onSubmit={handleSubmit}>
          <DialogTitle>{event ? 'Edit Event' : 'Add Event'}</DialogTitle>
          <Divider />
          <DialogContent sx={{ p: 3 }}>
            <Grid container spacing={gridSpacing}>
              <Grid size={{ xs: 12 }}>
                <TextField
                  fullWidth
                  label="Title"
                  {...getFieldProps('title')}
                  error={Boolean(touched.title && errors.title)}
                  helperText={touched.title && errors.title}
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField
                  fullWidth
                  multiline
                  rows={3}
                  label="Description"
                  {...getFieldProps('description')}
                  error={Boolean(touched.description && errors.description)}
                  helperText={touched.description && (errors.description as string)}
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <FormControlLabel
                  control={<Switch checked={values.allDay} {...getFieldProps('allDay')} />}
                  label="All day"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <MobileDateTimePicker
                  label="Start date"
                  value={new Date(values.start)}
                  format="dd/MM/yyyy hh:mm a"
                  onChange={date => setFieldValue('start', date)}
                  slotProps={{
                    textField: {
                      InputProps: {
                        endAdornment: (
                          <InputAdornment position="end" sx={{ cursor: 'pointer' }}>
                            <DateRangeIcon />
                          </InputAdornment>
                        ),
                      },
                    },
                  }}
                />
                {touched.start && errors.start && (
                  <FormHelperText error={true}>{errors.start as string}</FormHelperText>
                )}
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <MobileDateTimePicker
                  label="End date"
                  value={new Date(values.end)}
                  format="dd/MM/yyyy hh:mm a"
                  onChange={date => setFieldValue('end', date)}
                  slotProps={{
                    textField: {
                      InputProps: {
                        endAdornment: (
                          <InputAdornment position="end" sx={{ cursor: 'pointer' }}>
                            <DateRangeIcon />
                          </InputAdornment>
                        ),
                      },
                    },
                  }}
                />
                {touched.end && errors.end && (
                  <FormHelperText error={true}>{errors.end as string}</FormHelperText>
                )}
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12 }}>
                    <Typography variant="subtitle1">Background Color</Typography>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <FormControl>
                      <RadioGroup
                        row
                        aria-label="color"
                        {...getFieldProps('color')}
                        onChange={e => setFieldValue('color', e.target.value)}
                        name="color-radio-buttons-group"
                        sx={{ '& .MuiFormControlLabel-root': { mr: 0 } }}
                      >
                        {backgroundColor.map((item, index) => (
                          <ColorPalette
                            key={index}
                            value={item.value}
                            color={item.color}
                            label={item.label!}
                          />
                        ))}
                      </RadioGroup>
                    </FormControl>
                  </Grid>
                </Grid>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12 }}>
                    <Typography variant="subtitle1">Text Color</Typography>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <FormControl component="fieldset">
                      <RadioGroup
                        row
                        aria-label="textColor"
                        {...getFieldProps('textColor')}
                        onChange={e => setFieldValue('textColor', e.target.value)}
                        name="text-color-radio-buttons-group"
                        sx={{ '& .MuiFormControlLabel-root': { mr: 0 } }}
                      >
                        <FormControlLabel
                          value=""
                          control={<Radio color="default" />}
                          label="Default"
                          sx={{ pr: 1 }}
                        />
                        {textColor.map((item, index) => (
                          <ColorPalette
                            key={index}
                            value={item.value}
                            color={item.color}
                            label={item.label!}
                          />
                        ))}
                      </RadioGroup>
                    </FormControl>
                  </Grid>
                </Grid>
              </Grid>
            </Grid>
          </DialogContent>

          <DialogActions sx={{ p: 3 }}>
            <Grid container justifyContent="space-between" alignItems="center">
              <Grid>
                {!isCreating && (
                  <Tooltip title="Delete Event">
                    <IconButton onClick={() => handleDelete(event?.id)} size="large">
                      <DeleteIcon color="error" />
                    </IconButton>
                  </Tooltip>
                )}
              </Grid>
              <Grid>
                <Stack direction="row" spacing={2} alignItems="center">
                  <Button type="button" variant="outlined" onClick={onCancel}>
                    Cancel
                  </Button>
                  <Button type="submit" variant="contained" disabled={isSubmitting}>
                    {event ? 'Edit' : 'Add'}
                  </Button>
                </Stack>
              </Grid>
            </Grid>
          </DialogActions>
        </Form>
      </LocalizationProvider>
    </FormikProvider>
  );
};

export default AddEventFrom;
