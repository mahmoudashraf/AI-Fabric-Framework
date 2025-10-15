// material-ui
import { FormControlLabel, Radio, RadioGroup, Stack, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project-imports
import useConfig from 'hooks/useConfig';
import Avatar from 'ui-component/extended/Avatar';

// ==============================|| CUSTOMIZATION - MODE ||============================== //

const InputFilled = () => {
  const theme = useTheme();
  const { outlinedFilled, onChangeOutlinedField } = useConfig();

  const changeInputBackground = (e: any) => {
    const newOutlinedFilled = e.target.value === 'filled';
    if (newOutlinedFilled !== outlinedFilled) {
      onChangeOutlinedField(newOutlinedFilled);
    }
  };

  return (
    <Stack
      direction="row"
      alignItems="center"
      pb={2}
      px={2}
      justifyContent="space-between"
      sx={{ width: '100%' }}
    >
      <Typography variant="h5">INPUT BACKGROUND</Typography>
      <RadioGroup
        row
        aria-label="layout"
        value={outlinedFilled}
        onChange={changeInputBackground}
        name="row-radio-buttons-group"
      >
        <FormControlLabel
          control={<Radio value="filled" sx={{ display: 'none' }} />}
          label={
            <Avatar
              size="md"
              variant="rounded"
              outline
              sx={{
                width: 48,
                height: 30,
                bgcolor: theme.palette.mode === 'dark' ? 'dark.800' : 'grey.50',
                mr: 1,
                ...(!outlinedFilled && { borderColor: theme.palette.divider + 20 }),
              }}
            >
              {' '}
            </Avatar>
          }
        />
        <FormControlLabel
          control={<Radio value="outlined" sx={{ display: 'none' }} />}
          label={
            <Avatar
              size="md"
              variant="rounded"
              outline
              sx={{
                width: 48,
                height: 30,
                ...(outlinedFilled && { borderColor: theme.palette.divider }),
              }}
            >
              {' '}
            </Avatar>
          }
        />
      </RadioGroup>
    </Stack>
  );
};

export default InputFilled;
