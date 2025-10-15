'use client';

// material-ui
import AssignmentIcon from '@mui/icons-material/AssignmentIndTwoTone';
import CheckTwoToneIcon from '@mui/icons-material/CheckTwoTone';
import FolderIcon from '@mui/icons-material/FolderTwoTone';
import PageviewIcon from '@mui/icons-material/PageviewTwoTone';
import { AvatarGroup, Badge, Grid  } from '@mui/material';

// project imports
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';
import Avatar from 'ui-component/extended/Avatar';

// assets

const Avatar1 = '/assets/images/users/avatar-1.png';
const Avatar2 = '/assets/images/users/avatar-2.png';
const Avatar3 = '/assets/images/users/avatar-3.png';
const Avatar4 = '/assets/images/users/avatar-4.png';
const Avatar5 = '/assets/images/users/avatar-5.png';
const Profile = '/assets/images/users/profile.png';

// ===============================|| UI AVATAR ||=============================== //

const UIAvatar = () => (
  <MainCard>
    <MainCard.Header title="Avatar" action={<SecondaryAction link="https://next.material-ui.com/components/avatars/" />} />
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
        <SubCard title="Basic Avatar">
          <Grid container justifyContent="center">
            <Avatar color="default" />
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
        <SubCard title="Image Avatar">
          <Grid container spacing={3} justifyContent="center">
            <Grid>
              <Avatar alt="User 1" src={Avatar1} />
            </Grid>
            <Grid>
              <Avatar alt="User 2" src={Avatar2} />
            </Grid>
            <Grid>
              <Avatar alt="User 3" src={Avatar3} />
            </Grid>
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
        <SubCard title="Letter Avatar">
          <Grid container spacing={3} justifyContent="center">
            <Grid>
              <Avatar color="primary" sx={{ fontSize: '1rem' }}>
                AG
              </Avatar>
            </Grid>
            <Grid>
              <Avatar color="secondary">P</Avatar>
            </Grid>
            <Grid>
              <Avatar color="error">C</Avatar>
            </Grid>
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
        <SubCard title="Icon Avatar">
          <Grid container spacing={3} justifyContent="center">
            <Grid>
              <Avatar>
                <PageviewIcon />
              </Avatar>
            </Grid>
            <Grid>
              <Avatar color="info">
                <FolderIcon />
              </Avatar>
            </Grid>
            <Grid>
              <Avatar color="success">
                <AssignmentIcon />
              </Avatar>
            </Grid>
          </Grid>
        </SubCard>
      </Grid>

      <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
        <SubCard title="Variant Avatar">
          <Grid container spacing={3} justifyContent="center">
            <Grid>
              <Avatar color="primary">A</Avatar>
            </Grid>
            <Grid>
              <Avatar variant="square" color="warning">
                <FolderIcon />
              </Avatar>
            </Grid>
            <Grid>
              <Avatar variant="rounded" color="success">
                <AssignmentIcon />
              </Avatar>
            </Grid>
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
        <SubCard title="Outline Avatar">
          <Grid container spacing={3} justifyContent="center">
            <Grid>
              <Avatar color="primary" outline>
                A
              </Avatar>
            </Grid>
            <Grid>
              <Avatar variant="square" color="warning" outline>
                <FolderIcon />
              </Avatar>
            </Grid>
            <Grid>
              <Avatar variant="rounded" color="success" outline>
                <AssignmentIcon />
              </Avatar>
            </Grid>
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 6, lg: 3 }}>
        <SubCard title="Fallbacks Avatar">
          <Grid container spacing={3} justifyContent="center">
            <Grid>
              <Avatar alt="Remy Sharp" src="/broken-image.jpg" color="primary">
                A
              </Avatar>
            </Grid>
            <Grid>
              <Avatar alt="Remy Sharp" src="/broken-image.jpg" color="error" />
            </Grid>
            <Grid>
              <Avatar src="/broken-image.jpg" color="warning" />
            </Grid>
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6, md: 6, lg: 3 }}>
        <SubCard title="Grouped Avatar">
          <Grid container justifyContent="center">
            <AvatarGroup max={4}>
              <Avatar alt="User 1" src={Avatar1} />
              <Avatar alt="User 2" src={Avatar2} />
              <Avatar alt="User 3" src={Avatar3} />
              <Avatar alt="User 4" src={Avatar4} />
              <Avatar alt="User 5" src={Avatar5} />
            </AvatarGroup>
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 5 }}>
        <SubCard title="Color Variation Avatar">
          <Grid container spacing={3} justifyContent="center">
            <Grid>
              <Avatar color="primary">A</Avatar>
            </Grid>
            <Grid>
              <Avatar color="secondary">C</Avatar>
            </Grid>
            <Grid>
              <Avatar color="error">P</Avatar>
            </Grid>
            <Grid>
              <Avatar color="warning">
                <FolderIcon />
              </Avatar>
            </Grid>
            <Grid>
              <Avatar color="info">
                <AssignmentIcon />
              </Avatar>
            </Grid>
            <Grid>
              <Avatar color="success">
                <PageviewIcon />
              </Avatar>
            </Grid>
            <Grid>
              <Avatar>R</Avatar>
            </Grid>
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 5 }}>
        <SubCard title="Size Avatar">
          <Grid container spacing={3} justifyContent="center" alignItems="center">
            <Grid>
              <Avatar alt="User 1" src={Avatar1} size="xs" />
            </Grid>
            <Grid>
              <Avatar alt="User 2" src={Avatar2} size="sm" />
            </Grid>
            <Grid>
              <Avatar alt="User 3" src={Avatar3} size="md" />
            </Grid>
            <Grid>
              <Avatar alt="User 3" src={Avatar4} size="lg" />
            </Grid>
            <Grid>
              <Avatar alt="User 3" src={Avatar5} size="xl" />
            </Grid>
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, md: 2 }}>
        <SubCard title="Avatar with badge">
          <Grid container spacing={3} justifyContent="center" alignItems="center">
            <Grid>
              <Badge
                overlap="circular"
                badgeContent={
                  <Avatar color="success" size="badge">
                    <CheckTwoToneIcon />
                  </Avatar>
                }
              >
                <Avatar alt="User 1" src={Profile} size="xl" outline color="success" />
              </Badge>
            </Grid>
          </Grid>
        </SubCard>
      </Grid>
    </Grid>
  </MainCard>
);

export default UIAvatar;
