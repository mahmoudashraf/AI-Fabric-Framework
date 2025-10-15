import SupabaseLogin from '@/views/authentication/auth3/supabase-login';
import GuestGuard from '@/utils/route-guard/GuestGuard';

// ==============================|| PAGE ||============================== //

export default function LoginPage() {
  return (
    <GuestGuard>
      <SupabaseLogin />
    </GuestGuard>
  );
}
