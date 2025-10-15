import NextLink from 'next/link';

// project imports
import { DASHBOARD_PATH } from 'config';
import Logo from 'ui-component/Logo';

// ==============================|| MAIN LOGO ||============================== //

const LogoSection = () => (
  <NextLink href={DASHBOARD_PATH} aria-label="theme logo">
    <Logo />
  </NextLink>
);

export default LogoSection;
