// third-party
import { IconChartArcs, IconClipboardList, IconChartInfographic, IconUsers, IconRocket } from '@tabler/icons-react';
import { FormattedMessage } from 'react-intl';

// types
import { NavItemType } from 'types';

const icons = {
  IconChartArcs: IconChartArcs,
  IconClipboardList: IconClipboardList,
  IconChartInfographic: IconChartInfographic,
  IconUsers: IconUsers,
  IconRocket: IconRocket,
};

// ==============================|| MENU ITEMS - WIDGET ||============================== //

// Widget menu configuration
const widgetMenuConfig: NavItemType = {
  id: 'widget',
  title: <FormattedMessage id="widget" />,
  type: 'group',
  icon: icons.IconChartArcs,
  children: [
    {
      id: 'statistics',
      title: <FormattedMessage id="statistics" />,
      type: 'item',
      url: '/widget/statistics',
      icon: icons.IconChartArcs,
      breadcrumbs: false,
    },
    {
      id: 'statistics-optimized',
      title: 'Statistics Optimized',
      type: 'item',
      url: '/widget/statistics-optimized',
      icon: icons.IconRocket,
      breadcrumbs: false,
    },
    {
      id: 'data',
      title: <FormattedMessage id="data" />,
      type: 'item',
      url: '/widget/data',
      icon: icons.IconClipboardList,
      breadcrumbs: false,
    },
    {
      id: 'chart',
      title: <FormattedMessage id="chart" />,
      type: 'item',
      url: '/widget/chart',
      icon: icons.IconChartInfographic,
      breadcrumbs: false,
    },
    {
      id: 'crm',
      title: <FormattedMessage id="crm" />,
      type: 'item',
      url: '/widget/crm',
      icon: icons.IconUsers,
      breadcrumbs: false,
    },
  ],
};

export default widgetMenuConfig;
