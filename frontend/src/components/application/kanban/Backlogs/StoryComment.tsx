// material-ui
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
import { Card, Grid, Typography  } from '@mui/material';
import { useTheme } from '@mui/material/styles';

// project imports
import { KanbanComment, KanbanProfile } from 'types/kanban';
import Avatar from 'ui-component/extended/Avatar';

// assets

// types

interface Props {
  comment: KanbanComment;
  profile: KanbanProfile;
}

const avatarImage = '/assets/images/users';

// ==============================|| KANBAN BACKLOGS - STORY COMMENT ||============================== //

const StoryComment = ({ comment, profile }: Props) => {
  const theme = useTheme();

  return (
    <Card
      sx={{
        background:
          theme.palette.mode === 'dark' ? theme.palette.dark.main : theme.palette.grey[50],
        p: 1.5,
        mt: 1.25,
      }}
    >
      <Grid container spacing={1}>
        <Grid size={{ xs: 12 }}>
          <Grid container wrap="nowrap" alignItems="center" spacing={1}>
            <Grid>
              <Avatar
                sx={{ width: 24, height: 24 }}
                size="sm"
                alt="User 1"
                src={profile && profile.avatar && `${avatarImage}/${profile.avatar}`}
              />
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }}>
              <Grid container alignItems="center" spacing={1}>
                <Grid>
                  <Typography align="left" variant="h5" component="div">
                    {profile.name}
                  </Typography>
                </Grid>
                <Grid>
                  <Typography align="left" variant="caption">
                    <FiberManualRecordIcon
                      sx={{ width: 10, height: 10, opacity: 0.5, my: 0, mx: 0.625 }}
                    />
                    {profile.time}
                  </Typography>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12 }} sx={{ '&.MuiGrid-root': { pt: 1.5 } }}>
          <Typography align="left" variant="body2">
            {comment?.comment}
          </Typography>
        </Grid>
      </Grid>
    </Card>
  );
};

export default StoryComment;
