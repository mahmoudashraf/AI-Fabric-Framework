// This is example of menu item without group for horizontal layout. There will be no children.

// third-party
import { IconBrandChrome } from '@tabler/icons-react';
import { FormattedMessage } from 'react-intl';

// assets

// type
import { NavItemType } from 'types';

// ==============================|| MENU ITEMS - SAMPLE PAGE ||============================== //

const icons = {
  IconBrandChrome,
};
const samplePage: NavItemType = {
  id: 'sample-page',
  title: <FormattedMessage id="sample-page" />,
  icon: icons.IconBrandChrome,
  type: 'group',
  url: '/sample-page',
};

export default samplePage;
