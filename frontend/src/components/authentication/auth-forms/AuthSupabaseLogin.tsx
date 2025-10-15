'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Box,
  Button,
  Checkbox,
  FormControl,
  FormControlLabel,
  FormHelperText,
  IconButton,
  InputAdornment,
  InputLabel,
  OutlinedInput,
  Stack,
  Typography,
  Alert,
} from '@mui/material';
import { Visibility, VisibilityOff } from '@mui/icons-material';
import { useSupabaseAuth } from '@/contexts/SupabaseAuthContext';
import { DASHBOARD_PATH } from '@/config';
import SocialLoginButtons from './SocialLoginButtons';

const AuthSupabaseLogin = () => {
  const router = useRouter();
  const { signIn } = useSupabaseAuth();
  
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);
  const [errors, setErrors] = useState<{ email?: string; password?: string; general?: string }>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  const validateForm = (): boolean => {
    const newErrors: { email?: string; password?: string } = {};

    if (!email) {
      newErrors.email = 'Email is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      newErrors.email = 'Please enter a valid email address';
    } else if (email.length > 254) {
      newErrors.email = 'Email address is too long';
    }

    if (!password) {
      newErrors.password = 'Password is required';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrors({});

    if (!validateForm()) {
      return;
    }

    setIsSubmitting(true);

    try {
      await signIn(email, password);
      router.push(DASHBOARD_PATH);
    } catch (error: any) {
      setErrors({
        general: error.message || 'Failed to sign in. Please check your credentials.',
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <Stack spacing={3}>
        {errors.general && (
          <Alert severity="error">{errors.general}</Alert>
        )}

        <FormControl fullWidth error={!!errors.email}>
          <InputLabel htmlFor="email-login">Email Address</InputLabel>
          <OutlinedInput
            id="email-login"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            label="Email Address"
            inputProps={{}}
          />
          {errors.email && <FormHelperText>{errors.email}</FormHelperText>}
        </FormControl>

        <FormControl fullWidth error={!!errors.password}>
          <InputLabel htmlFor="password-login">Password</InputLabel>
          <OutlinedInput
            id="password-login"
            type={showPassword ? 'text' : 'password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            endAdornment={
              <InputAdornment position="end">
                <IconButton
                  aria-label="toggle password visibility"
                  onClick={() => setShowPassword(!showPassword)}
                  edge="end"
                  size="large"
                >
                  {showPassword ? <Visibility /> : <VisibilityOff />}
                </IconButton>
              </InputAdornment>
            }
            label="Password"
          />
          {errors.password && <FormHelperText>{errors.password}</FormHelperText>}
        </FormControl>

        <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1}>
          <FormControlLabel
            control={
              <Checkbox
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
                name="rememberMe"
                color="primary"
              />
            }
            label="Remember me"
          />
          <Typography
            variant="subtitle1"
            color="secondary"
            sx={{ textDecoration: 'none', cursor: 'pointer' }}
          >
            Forgot Password?
          </Typography>
        </Stack>

        <Box>
          <Button
            disableElevation
            disabled={isSubmitting}
            fullWidth
            size="large"
            type="submit"
            variant="contained"
            color="secondary"
          >
            {isSubmitting ? 'Signing In...' : 'Sign In'}
          </Button>
        </Box>

        <Box sx={{ mt: 3 }}>
          <Typography
            variant="body2"
            color="textSecondary"
            sx={{ textAlign: 'center', mb: 2 }}
          >
            Or continue with
          </Typography>
          <SocialLoginButtons
            onError={(error) => setErrors({ general: error })}
            onSuccess={() => router.push(DASHBOARD_PATH)}
          />
        </Box>
      </Stack>
    </form>
  );
};

export default AuthSupabaseLogin;
