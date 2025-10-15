// third-party
import { IconHelp, IconSitemap } from '@tabler/icons-react';
import { FormattedMessage } from 'react-intl';

// types
import { NavItemType } from 'types';

// assets

// constant
const icons = {
  IconHelp,
  IconSitemap,
};

// ==============================|| SAMPLE PAGE & DOCUMENTATION MENU ITEMS ||============================== //

const other: NavItemType = {
  id: 'sample-docs-roadmap',
  icon: IconHelp,
  type: 'group',
  children: [
    {
      id: 'documentation',
      title: <FormattedMessage id="documentation" />,
      type: 'item',
      url: 'https://codedthemes.gitbook.io/kiwi/',
      icon: icons.IconHelp,
      external: true,
      target: true,
    },
    {
      id: 'roadmap',
      title: <FormattedMessage id="roadmap" />,
      type: 'item',
      url: 'https://codedthemes.gitbook.io/kiwi/roadmap',
      icon: icons.IconSitemap,
      external: true,
      target: true,
    },
  ],
};

export default other;
