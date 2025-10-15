// material-ui
import { Avatar, Badge } from '@mui/material';

// project imports
import { UserProfile } from 'types/user-profile';

import AvatarStatus from './AvatarStatus';

// types

// assets
const avatarImage = '/assets/images/users';

// ==============================|| CHAT USER AVATAR WITH STATUS ICON ||============================== //

interface UserAvatarProps {
  user: UserProfile;
}

const UserAvatar = ({ user }: UserAvatarProps) => (
  <Badge
    overlap="circular"
    badgeContent={<AvatarStatus status={user.online_status!} />}
    anchorOrigin={{
      vertical: 'bottom',
      horizontal: 'right',
    }}
  >
     <Avatar alt={user.name || 'User'} src={user.avatar ? `${avatarImage}/${user.avatar}` : ''} />
  </Badge>
);

export default UserAvatar;
