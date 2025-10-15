import { Roboto } from 'next/font/google';

import { LAYOUT_CONST } from 'constant';

// third party

// types
import { ConfigProps } from 'types/config';

// basename: only at build time to set, and Don't add '/' at end off BASENAME for breadcrumbs, also Don't put only '/' use blank('') instead,
// like '/kiwi-material-react/react/default'
export const BASE_PATH = '';

export const DASHBOARD_PATH = '/dashboard/default';
export const HORIZONTAL_MAX_ITEM = 7;

const roboto = Roboto({ subsets: ['latin'], weight: ['300', '400', '500', '700'] });

const config: ConfigProps = {
  layout: LAYOUT_CONST.VERTICAL_LAYOUT, // vertical, horizontal
  drawerType: LAYOUT_CONST.DEFAULT_DRAWER, // default, mini-drawer
  fontFamily: roboto.style.fontFamily,
  borderRadius: 8,
  outlinedFilled: true,
  navType: 'light', // light, dark
  presetColor: 'default', // default, theme1, theme2, theme3, theme4, theme5, theme6
  locale: 'en', // 'en' - English, 'fr' - French, 'ro' - Romanian, 'zh' - Chinese
  rtlLayout: false,
  container: false,
};

export default config;
