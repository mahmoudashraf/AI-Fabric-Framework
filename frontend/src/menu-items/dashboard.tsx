// third-party
import { IconDashboard, IconDeviceAnalytics } from '@tabler/icons-react';
import { FormattedMessage } from 'react-intl';

// assets

// type
import { NavItemType } from 'types';

const icons = {
  IconDashboard: IconDashboard,
  IconDeviceAnalytics: IconDeviceAnalytics,
};

// ==============================|| MENU ITEMS - DASHBOARD ||============================== //

const dashboard: NavItemType = {
  id: 'dashboard',
  title: <FormattedMessage id="dashboard" />,
  icon: icons.IconDashboard,
  type: 'group',
  children: [],
};

export default dashboard;
