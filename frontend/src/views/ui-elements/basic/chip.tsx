'use client';

// material-ui
import { Avatar, Grid  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';
import Chip from 'ui-component/extended/Chip';

// assets
const User1 = '/assets/images/users/avatar-1.png';

// ================================|| UI CHIP ||================================ //

const UIChip = () => {
  const theme = useTheme();

  const handleDelete = () => {
    console.info('You clicked the delete icon.');
  };

  const handleClick = () => {
    console.info('You clicked the Chip.');
  };

  return (
    <MainCard>
      <MainCard.Header title="Chip" action={<SecondaryAction link="https://next.material-ui.com/components/chips/" />} />
      <Grid container spacing={gridSpacing}>
        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Basic">
            <Grid container spacing={3}>
              <Grid>
                <Chip label="Default" />
              </Grid>
              <Grid>
                <Chip label="Deletable" onDelete={handleDelete} />
              </Grid>
              <Grid>
                <Chip label="Disabled" disabled />
              </Grid>
              <Grid>
                <Chip label="Clickable" onClick={handleClick} onDelete={handleDelete} />
              </Grid>

              <Grid>
                <Chip
                  avatar={<Avatar>AK</Avatar>}
                  label="Clickable deletable"
                  onClick={handleClick}
                  onDelete={handleDelete}
                  color="primary"
                />
              </Grid>
              <Grid>
                <Chip
                  label="Clickable"
                  avatar={<Avatar alt="Avatar 1" src={User1} />}
                  onClick={handleClick}
                />
              </Grid>
            </Grid>
          </SubCard>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <SubCard title="Outlined">
            <Grid container spacing={3}>
              <Grid>
                <Chip label="Default" variant="outlined" />
              </Grid>
              <Grid>
                <Chip label="Deletable" onDelete={handleDelete} variant="outlined" />
              </Grid>
              <Grid>
                <Chip label="Disabled" disabled variant="outlined" />
              </Grid>
              <Grid>
                <Chip
                  label="Clickable"
                  variant="outlined"
                  onClick={handleClick}
                  onDelete={handleDelete}
                />
              </Grid>

              <Grid>
                <Chip
                  avatar={<Avatar>AK</Avatar>}
                  label="Clickable deletable"
                  onClick={handleClick}
                  onDelete={handleDelete}
                  color="primary"
                  variant="outlined"
                />
              </Grid>
              <Grid>
                <Chip
                  label="Clickable"
                  avatar={<Avatar alt="Avatar 1" src={User1} />}
                  onClick={handleClick}
                  variant="outlined"
                />
              </Grid>
            </Grid>
          </SubCard>
        </Grid>

        <Grid size={{ xs: 12 }}>
          <SubCard title="Outlined Color">
            <Grid container spacing={3}>
              <Grid>
                <Chip label="Default" variant="outlined" />
              </Grid>
              <Grid>
                <Chip label="Secondary" variant="outlined" chipcolor="secondary" />
              </Grid>
              <Grid>
                <Chip
                  label="Success"
                  onDelete={handleDelete}
                  variant="outlined"
                  chipcolor="success"
                />
              </Grid>
              <Grid>
                <Chip label="Error" onDelete={handleDelete} variant="outlined" chipcolor="error" />
              </Grid>
              <Grid>
                <Chip
                  label="secondary"
                  onDelete={handleDelete}
                  variant="outlined"
                  chipcolor="secondary"
                />
              </Grid>
              <Grid>
                <Chip label="Disabled" disabled variant="outlined" />
              </Grid>
              <Grid>
                <Chip
                  label="Clickable"
                  variant="outlined"
                  onClick={handleClick}
                  onDelete={handleDelete}
                />
              </Grid>

              <Grid>
                <Chip
                  avatar={
                    <Avatar
                      sx={{
                        bgcolor: theme.palette.warning.dark,
                        color: `${theme.palette.background.paper} !important`,
                      }}
                    >
                      AK
                    </Avatar>
                  }
                  label="Clickable deletable"
                  onClick={handleClick}
                  onDelete={handleDelete}
                  variant="outlined"
                  chipcolor="warning"
                />
              </Grid>
              <Grid>
                <Chip
                  label="Clickable"
                  avatar={<Avatar alt="Avatar 1" src={User1} />}
                  onClick={handleClick}
                  variant="outlined"
                />
              </Grid>
              <Grid>
                <Chip
                  label="Clickable"
                  avatar={<Avatar alt="Avatar 1" src={User1} />}
                  onClick={handleClick}
                  variant="outlined"
                  chipcolor="secondary"
                />
              </Grid>
            </Grid>
          </SubCard>
        </Grid>

        <Grid size={{ xs: 12 }}>
          <SubCard title="Filled Color">
            <Grid container spacing={3}>
              <Grid>
                <Chip label="Default" />
              </Grid>
              <Grid>
                <Chip label="Secondary" chipcolor="secondary" />
              </Grid>
              <Grid>
                <Chip label="Success" onDelete={handleDelete} chipcolor="success" />
              </Grid>
              <Grid>
                <Chip label="Error" onDelete={handleDelete} chipcolor="error" />
              </Grid>
              <Grid>
                <Chip label="secondary" onDelete={handleDelete} chipcolor="secondary" />
              </Grid>
              <Grid>
                <Chip label="Disabled" disabled />
              </Grid>
              <Grid>
                <Chip label="Clickable" onClick={handleClick} onDelete={handleDelete} />
              </Grid>

              <Grid>
                <Chip
                  avatar={
                    <Avatar
                      sx={{
                        bgcolor: theme.palette.warning.dark,
                        color: `${theme.palette.background.paper} !important`,
                      }}
                    >
                      AK
                    </Avatar>
                  }
                  label="Clickable delete"
                  onClick={handleClick}
                  onDelete={handleDelete}
                  chipcolor="warning"
                />
              </Grid>
              <Grid>
                <Chip
                  label="Clickable"
                  avatar={<Avatar alt="Avatar 1" src={User1} />}
                  onClick={handleClick}
                />
              </Grid>
              <Grid>
                <Chip
                  label="Clickable"
                  avatar={<Avatar alt="Avatar 1" src={User1} />}
                  onClick={handleClick}
                  chipcolor="secondary"
                />
              </Grid>
            </Grid>
          </SubCard>
        </Grid>
      </Grid>
    </MainCard>
  );
};

export default UIChip;
