// PROJECT IMPORTS
import Login from 'views/authentication/auth1/login';
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
