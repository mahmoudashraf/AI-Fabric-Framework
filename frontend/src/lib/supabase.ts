import { createClient } from '@supabase/supabase-js';

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || '';
const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';
const isDevelopment = process.env.NEXT_PUBLIC_ENVIRONMENT === 'development';
const mockAuthEnabled = process.env.NEXT_PUBLIC_MOCK_AUTH_ENABLED === 'true';

// Only show error if not in development mode with mock auth enabled
if ((!supabaseUrl || !supabaseAnonKey) && !(isDevelopment && mockAuthEnabled)) {
  console.error('❌ Supabase configuration missing!');
  console.error('Please create frontend/.env.local with:');
  console.error('NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co');
  console.error('NEXT_PUBLIC_SUPABASE_ANON_KEY=your-anon-key');
}

// Create a mock client if credentials are missing to prevent crashes
const createMockClient = () => ({
  auth: {
    signUp: async () => ({ data: null, error: { message: 'Supabase not configured' } }),
    signIn: async () => ({ data: null, error: { message: 'Supabase not configured' } }),
    signInWithPassword: async () => ({ data: null, error: { message: 'Supabase not configured' } }),
    signOut: async () => ({ error: null }),
    getSession: async () => ({ data: { session: null }, error: null }),
    onAuthStateChange: () => ({ data: { subscription: { unsubscribe: () => {} } } }),
  },
});

export const supabase = (!supabaseUrl || !supabaseAnonKey) 
  ? createMockClient() as any
  : createClient(supabaseUrl, supabaseAnonKey, {
      auth: {
        persistSession: true,
        autoRefreshToken: true,
        detectSessionInUrl: true,
        flowType: 'implicit',
      },
    });
