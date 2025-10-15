// material-ui
import ChatBubbleTwoToneIcon from '@mui/icons-material/ChatBubbleTwoTone';
import PhoneTwoToneIcon from '@mui/icons-material/PhoneTwoTone';
import { Button, Grid, Tooltip, Typography  } from '@mui/material';
import { styled, useTheme } from '@mui/material/styles';

// project imports
import { gridSpacing } from 'constants/index';
import { UserProfile } from 'types/user-profile';

import Avatar from '../extended/Avatar';

// types

// assets

const avatarImage = '/assets/images/users';

// styles
const ListWrapper = styled('div')(({ theme }) => ({
  padding: '15px 0',
  borderBottom: theme.palette.mode === 'dark' ? 'none' : '1px solid',
  borderTop: theme.palette.mode === 'dark' ? 'none' : '1px solid',
  borderColor: `${theme.palette.grey[100]}!important`,
}));

// ==============================|| USER CONTACT LIST ||============================== //

interface ContactListProps extends UserProfile {
  onActive: () => void;
}

const ContactList = ({ avatar, name, role, onActive }: ContactListProps) => {
  const theme = useTheme();
  const avatarProfile = avatar && `${avatarImage}/${avatar}`;

  return (
    <ListWrapper>
      <Grid container alignItems="center" spacing={gridSpacing}>
        <Grid size={{ xs: 12, sm: 6 }}
          onClick={() => {
            if (onActive) onActive();
          }}
          style={{ cursor: 'pointer' }}
        >
          <Grid container alignItems="center" spacing={gridSpacing} sx={{ flexWrap: 'nowrap' }}>
            <Grid>
              <Avatar alt={name || ''} src={avatarProfile || ''} sx={{ width: 48, height: 48 }} />
            </Grid>
            <Grid size="grow" sx={{ minWidth: 0 }}>
              <Grid container spacing={0}>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="h4" component="div">
                    {name}
                  </Typography>
                </Grid>
                <Grid size={{ xs: 12 }}>
                  <Typography variant="caption">{role}</Typography>
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12, sm: 6 }}>
          <Grid
            container
            spacing={1}
            sx={{
              justifyContent: 'flex-end',
              [theme.breakpoints.down('md')]: { justifyContent: 'flex-start' },
            }}
          >
            <Grid>
              <Tooltip placement="top" title="Message">
                <Button variant="outlined" sx={{ minWidth: 32, height: 32, p: 0 }}>
                  <ChatBubbleTwoToneIcon fontSize="small" />
                </Button>
              </Tooltip>
            </Grid>
            <Grid>
              <Tooltip placement="top" title="Call">
                <Button
                  variant="outlined"
                  color="secondary"
                  sx={{ minWidth: 32, height: 32, p: 0 }}
                >
                  <PhoneTwoToneIcon fontSize="small" />
                </Button>
              </Tooltip>
            </Grid>
          </Grid>
        </Grid>
      </Grid>
    </ListWrapper>
  );
};

export default ContactList;
