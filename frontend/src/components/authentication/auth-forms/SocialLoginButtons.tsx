'use client';

import React, { useState } from 'react';
import {
  Box,
  Button,
  Stack,
  Typography,
  CircularProgress,
  Alert,
} from '@mui/material';
import {
  Apple as AppleIcon,
  Google as GoogleIcon,
  LinkedIn as LinkedInIcon,
  Facebook as FacebookIcon,
} from '@mui/icons-material';
import { supabase } from '@/lib/supabase';

interface SocialLoginButtonsProps {
  onError?: (error: string) => void;
  onSuccess?: () => void;
}

const SocialLoginButtons: React.FC<SocialLoginButtonsProps> = ({ 
  onError, 
  onSuccess 
}) => {
  const [loading, setLoading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Check if Supabase is properly configured
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || '';
  const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';
  const isSupabaseConfigured = supabaseUrl && supabaseAnonKey && 
    supabaseUrl !== 'https://your-project.supabase.co' && 
    supabaseAnonKey !== 'your-anon-key';

  // If Supabase is not configured, show a message instead of buttons
  if (!isSupabaseConfigured) {
    return (
      <Box sx={{ mt: 3 }}>
        <Typography
          variant="body2"
          color="textSecondary"
          sx={{ textAlign: 'center', mb: 2 }}
        >
          Social login requires Supabase configuration
        </Typography>
        <Alert severity="info" sx={{ textAlign: 'center' }}>
          <Typography variant="body2">
            To enable social login, please configure Supabase credentials in your environment variables.
          </Typography>
        </Alert>
      </Box>
    );
  }

  const handleSocialLogin = async (provider: 'google' | 'apple' | 'linkedin' | 'facebook') => {
    setLoading(provider);
    setError(null);

    try {
      // Use LinkedIn OIDC provider instead of legacy LinkedIn
      const actualProvider = provider === 'linkedin' ? 'linkedin_oidc' : provider;
      
      const options: any = {
        redirectTo: `${window.location.origin}/auth/callback`,
      };

      // Add LinkedIn-specific scopes
      if (provider === 'linkedin') {
        options.scopes = 'openid profile email';
      } else {
        options.queryParams = {
          access_type: 'offline',
          prompt: 'consent',
        };
      }

      const { error } = await supabase.auth.signInWithOAuth({
        provider: actualProvider,
        options,
      });

      if (error) {
        throw error;
      }

      // The OAuth flow will redirect the user, so we don't need to handle success here
      console.log('OAuth redirect initiated for', provider);
      onSuccess?.();
    } catch (err: any) {
      const errorMessage = err.message || `Failed to sign in with ${provider}`;
      setError(errorMessage);
      onError?.(errorMessage);
      console.error(`Error signing in with ${provider}:`, err);
    } finally {
      setLoading(null);
    }
  };

  const socialButtons = [
    {
      provider: 'google' as const,
      label: 'Continue with Google',
      icon: <GoogleIcon />,
      color: '#4285F4',
      textColor: '#ffffff',
    },
    {
      provider: 'apple' as const,
      label: 'Continue with Apple',
      icon: <AppleIcon />,
      color: '#000000',
      textColor: '#ffffff',
    },
    {
      provider: 'linkedin' as const,
      label: 'Continue with LinkedIn',
      icon: <LinkedInIcon />,
      color: '#0077B5',
      textColor: '#ffffff',
    },
    {
      provider: 'facebook' as const,
      label: 'Continue with Facebook',
      icon: <FacebookIcon />,
      color: '#1877F2',
      textColor: '#ffffff',
    },
  ];

  return (
    <Box>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Stack spacing={2}>
        {socialButtons.map(({ provider, label, icon, color, textColor }) => (
          <Button
            key={provider}
            fullWidth
            variant="outlined"
            size="large"
            onClick={() => handleSocialLogin(provider)}
            disabled={loading === provider}
            startIcon={loading === provider ? <CircularProgress size={20} /> : icon}
            sx={{
              backgroundColor: color,
              color: textColor,
              borderColor: color,
              '&:hover': {
                backgroundColor: color,
                opacity: 0.9,
                borderColor: color,
              },
              '&:disabled': {
                backgroundColor: color,
                opacity: 0.6,
                color: textColor,
              },
            }}
          >
            {loading === provider ? 'Connecting...' : label}
          </Button>
        ))}
      </Stack>

      <Box sx={{ mt: 2, textAlign: 'center' }}>
        <Typography variant="body2" color="textSecondary">
          By continuing, you agree to our Terms of Service and Privacy Policy
        </Typography>
      </Box>
    </Box>
  );
};

export default SocialLoginButtons;
