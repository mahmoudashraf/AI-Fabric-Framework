// third-party
import { IconDashboard } from '@tabler/icons-react';

// type
import { NavItemType } from 'types';

const icons = {
  IconDashboard
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
    }
  ]
};

export default easyluxury;
