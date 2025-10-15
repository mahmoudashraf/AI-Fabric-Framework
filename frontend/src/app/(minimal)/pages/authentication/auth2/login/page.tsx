// PROJECT IMPORTS
import Login from 'views/authentication/auth2/login';
import GuestGuard from 'utils/route-guard/GuestGuard';

// ================================|| LOGIN ||================================ //

const LoginPage = () => {
  return (
    <GuestGuard>
      <Login />
    </GuestGuard>
  );
};

export default LoginPage;
