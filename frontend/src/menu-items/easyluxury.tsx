// third-party
import { IconDashboard, IconRobot } from '@tabler/icons-react';

// type
import { NavItemType } from 'types';

const icons = {
  IconDashboard,
  IconRobot
};

// ==============================|| MENU ITEMS - ADMIN ||============================== //

const easyluxury: NavItemType = {
  id: 'admin',
  title: 'Admin',
  type: 'group',
  children: [
    {
      id: 'dashboard',
      title: 'Dashboard',
      type: 'item',
      url: '/admin/dashboard',
      icon: icons.IconDashboard,
      breadcrumbs: true
    },
    {
      id: 'ai-profile',
      title: 'AI Profile',
      type: 'item',
      url: '/apps/user/social-profile/ai-profile',
      icon: icons.IconRobot,
      breadcrumbs: true
    }
  ]
};

export default easyluxury;
