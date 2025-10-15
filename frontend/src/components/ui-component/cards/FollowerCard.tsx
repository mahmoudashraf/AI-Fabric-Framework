'use client';

// material-ui

// assets
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import FavoriteTwoToneIcon from '@mui/icons-material/FavoriteTwoTone';
import GroupTwoToneIcon from '@mui/icons-material/GroupTwoTone';
import MoreHorizOutlinedIcon from '@mui/icons-material/MoreHorizOutlined';
import PeopleAltTwoToneIcon from '@mui/icons-material/PeopleAltTwoTone';
import PersonAddTwoToneIcon from '@mui/icons-material/PersonAddTwoTone';
import PinDropTwoToneIcon from '@mui/icons-material/PinDropTwoTone';
import { Avatar,
  Button,
  Card,
  Grid,
  ListItemIcon,
  Menu,
  MenuItem,
  Typography,
 } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState, SyntheticEvent } from 'react';

// types
import { FollowerCardProps } from 'types/user';

// ==============================|| SOCIAL PROFILE - FOLLOWER CARD ||============================== //

const FollowerCard = ({ avatar, follow, location, name }: FollowerCardProps) => {
  const theme = useTheme();
  const avatarImage = `/assets/images/users/${avatar}`;
  const [anchorEl, setAnchorEl] = useState<Element | (() => Element) | null | undefined>(null);
  const handleClick = (event: SyntheticEvent) => {
    setAnchorEl(event?.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  return (
    <Card
      sx={{
        padding: '16px',
        background:
          theme.palette.mode === 'dark' ? theme.palette.dark.main : theme.palette.grey[50],
        border: '1px solid',
        borderColor:
          theme.palette.mode === 'dark' ? theme.palette.dark.main : theme.palette.grey[100],
        '&:hover': {
          border: `1px solid${theme.palette.primary.main}`,
        },
      }}
    >
      <Grid container spacing={2}>
        <Grid size={{ xs: 12 }}>
          <Grid container spacing={2}>
            <Grid>
              <Avatar alt="User 1" src={avatarImage} />
            </Grid>
            <Grid size={{ xs: 12 }} sx={{ minWidth: 0 }}>
              <Typography
                variant="h5"
                component="div"
                sx={{
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  display: 'block',
                }}
              >
                {name}
              </Typography>
              <Typography
                variant="subtitle2"
                sx={{
                  mt: 0.25,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  display: 'block',
                }}
              >
                <PinDropTwoToneIcon
                  sx={{ mr: '6px', fontSize: '16px', verticalAlign: 'text-top' }}
                />
                {location}
              </Typography>
            </Grid>
            <Grid>
              <MoreHorizOutlinedIcon
                fontSize="small"
                sx={{
                  color: theme.palette.primary[200],
                  cursor: 'pointer',
                }}
                aria-controls="menu-followers-card"
                aria-haspopup="true"
                onClick={handleClick}
              />
              <Menu
                id="menu-followers-card"
                anchorEl={anchorEl}
                keepMounted
                open={Boolean(anchorEl)}
                onClose={handleClose}
                variant="selectedMenu"
                anchorOrigin={{
                  vertical: 'bottom',
                  horizontal: 'right',
                }}
                transformOrigin={{
                  vertical: 'top',
                  horizontal: 'right',
                }}
              >
                <MenuItem onClick={handleClose}>
                  <ListItemIcon>
                    <FavoriteTwoToneIcon fontSize="small" />
                  </ListItemIcon>
                  Favorites
                </MenuItem>
                <MenuItem onClick={handleClose}>
                  <ListItemIcon>
                    <GroupTwoToneIcon fontSize="small" />
                  </ListItemIcon>
                  Edit Friend List
                </MenuItem>
                <MenuItem onClick={handleClose}>
                  <ListItemIcon>
                    <DeleteTwoToneIcon fontSize="small" />
                  </ListItemIcon>
                  Removed
                </MenuItem>
              </Menu>
            </Grid>
          </Grid>
        </Grid>
        <Grid size={{ xs: 12 }}>
          {follow === 2 ? (
            <Button variant="contained" fullWidth startIcon={<PersonAddTwoToneIcon />}>
              Follow Back
            </Button>
          ) : (
            <Button variant="outlined" fullWidth startIcon={<PeopleAltTwoToneIcon />}>
              Followed
            </Button>
          )}
        </Grid>
      </Grid>
    </Card>
  );
};

export default FollowerCard;
